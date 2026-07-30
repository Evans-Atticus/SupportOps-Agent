package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Spring 注入所有策略后构造 O(1) 注册表，并在启动时拒绝重复 Handler。 */
@Component
public class DiagnosisHandlerRegistry {
    private final Map<ScenarioType, ScenarioDiagnosisHandler> handlers;

    public DiagnosisHandlerRegistry(List<ScenarioDiagnosisHandler> candidates) {
        EnumMap<ScenarioType, ScenarioDiagnosisHandler> indexed = new EnumMap<>(ScenarioType.class);
        for (ScenarioDiagnosisHandler candidate : candidates) {
            // put 返回旧值说明出现重复策略；启动时立即失败比运行时随机覆盖更安全。
            if (indexed.put(candidate.supports(), candidate) != null) {
                throw new IllegalStateException("场景存在重复 Handler: " + candidate.supports());
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    public ScenarioDiagnosisHandler required(ScenarioType scenarioType) {
        // 应用服务只提供场景枚举，不接收客户端传入的 Bean 名或 Java 类名。
        ScenarioDiagnosisHandler handler = handlers.get(scenarioType);
        if (handler == null) {
            throw new BusinessException(ErrorCode.UNKNOWN_SCENARIO);
        }
        return handler;
    }
}
