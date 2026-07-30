package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.ApiCallRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** API 频繁失败：计算固定十分钟窗口的失败率与错误码分布。 */
@Component
public class ApiFrequentFailureHandler implements ScenarioDiagnosisHandler {
    /** 失败调用占比达到 50% 即进入事件排查流程。 */
    private static final double FAILURE_THRESHOLD = 0.5;

    @Override
    public ScenarioType supports() { return ScenarioType.API_FREQUENT_FAILURE; }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        // 以样本中的最新调用为窗口终点，使历史演示数据也能稳定复现十分钟统计。
        LocalDateTime windowEnd = context.apiCalls().stream().map(ApiCallRecord::calledAt).max(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowStart = windowEnd.minusMinutes(10);
        List<ApiCallRecord> window = context.apiCalls().stream()
                .filter(call -> !call.calledAt().isBefore(windowStart) && !call.calledAt().isAfter(windowEnd)).toList();
        long failures = window.stream().filter(call -> "FAILED".equals(call.requestStatus())).count();
        // 强制转为 double，避免整数除法把 5/6 截断成 0。
        double rate = window.isEmpty() ? 0 : (double) failures / window.size();
        // groupingBy + counting 将失败样本按错误码聚合，给技术支持提供排查方向。
        Map<String, Long> distribution = window.stream().filter(call -> call.errorCode() != null)
                .collect(Collectors.groupingBy(ApiCallRecord::errorCode, Collectors.counting()));
        // 阈值比较集中在一个布尔值中，后续结论、建议和置信度使用同一判断结果。
        boolean abnormal = rate >= FAILURE_THRESHOLD;
        return new DiagnosisResult("最近十分钟调用窗口统计完成",
                abnormal ? "API 失败率达到告警阈值，错误分布：" + distribution : "API 失败率未达到告警阈值",
                abnormal ? "关联服务事件并检查上游超时与熔断状态" : "继续观察调用指标",
                "您好，我们已确认接口近期存在较高失败率，技术支持正在依据错误码分布排查。",
                abnormal ? 0.99 : 0.85,
                List.of(
                        HandlerSupport.evidence("api_call_records", null, "failure_rate", "十分钟失败率",
                                String.format("%.2f", rate), failures + "/" + window.size() + " 次失败"),
                        HandlerSupport.evidence("api_call_records", null, "error_code", "错误码分布",
                                distribution, "按错误码聚合失败调用")
                ));
    }
}
