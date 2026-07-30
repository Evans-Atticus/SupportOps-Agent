package com.example.supportops.module.diagnosis.application;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.business.model.query.ApiCallRecord;
import com.example.supportops.module.business.model.query.CouponRecord;
import com.example.supportops.module.business.model.query.InvoiceRecord;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.MemberRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.business.model.query.RefundRecord;
import com.example.supportops.module.business.model.query.SopRecord;
import com.example.supportops.module.business.service.BusinessQueryService;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.trace.integration.TraceDataGateway;
import com.example.supportops.module.trace.model.TraceModels;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeSnippet;
import com.example.supportops.module.knowledge.service.ProductKnowledgeService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 执行已登记的查询计划，并把 DAO 返回值冻结成规则 Handler 的只读上下文。 */
@Component
public class DiagnosisContextFactory {
    private final BusinessQueryService businessQueryService;
    private final TraceDataGateway traceDataGateway;
    private final ProductKnowledgeService productKnowledgeService;

    public DiagnosisContextFactory(BusinessQueryService businessQueryService, TraceDataGateway traceDataGateway,
                                   ProductKnowledgeService productKnowledgeService) {
        this.businessQueryService = businessQueryService;
        this.traceDataGateway = traceDataGateway;
        this.productKnowledgeService = productKnowledgeService;
    }

    /**
     * 根据场景执行固定查询，并构造 Handler 的完整输入。
     * @param scenario 已通过白名单校验的场景
     * @param ticketNo 用于报告追踪的工单号
     * @param businessNo 订单号、会员号或 API 客户端号等场景业务键
     */
    public DiagnosisContext create(ScenarioType scenario, String ticketNo, String businessNo) {
        return create(scenario, ticketNo, businessNo, "");
    }

    public DiagnosisContext create(ScenarioType scenario, String ticketNo, String businessNo, String question) {
        if (businessNo == null || businessNo.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_BUSINESS_NO);
        }
        List<OrderRecord> orders = List.of();
        List<PaymentRecord> payments = List.of();
        List<RefundRecord> refunds = List.of();
        List<CouponRecord> coupons = List.of();
        List<MemberRecord> members = List.of();
        List<LogisticsRecord> logistics = List.of();
        List<ApiCallRecord> apiCalls = List.of();
        List<InvoiceRecord> invoices = List.of();
        List<ProductKnowledgeSnippet> productKnowledge = List.of();
        TraceModels.TraceDetail traceDetail = null;

        // switch 表达式把每个场景的数据边界写死；这里是编排，不承载根因判断。
        switch (scenario) {
            case ORDER_INFORMATION_QUERY -> orders = businessQueryService.order(businessNo).records();
            case PRODUCT_INFORMATION_QUERY, PRODUCT_USAGE_GUIDANCE, PRODUCT_TROUBLESHOOTING -> {
                orders = businessQueryService.order(businessNo).records();
                int topK = scenario == ScenarioType.PRODUCT_TROUBLESHOOTING ? 2 : 1;
                productKnowledge = productKnowledgeService.retrieveForOrder(businessNo,
                        productSearchQuery(scenario, question), topK);
            }
            case PAYMENT_SUCCESS_ORDER_PENDING -> {
                orders = businessQueryService.order(businessNo).records();
                payments = businessQueryService.paymentsByOrder(businessNo).records();
            }
            case ORDER_CANCELLED_BUT_CHARGED -> {
                orders = businessQueryService.order(businessNo).records();
                payments = businessQueryService.paymentsByOrder(businessNo).records();
                refunds = businessQueryService.refundsByOrder(businessNo).records();
            }
            case COUPON_UNAVAILABLE -> {
                orders = businessQueryService.order(businessNo).records();
                OrderRecord order = orders.get(0);
                // 优惠券编码由已查询的订单事实确定，不接受客户端额外指定，避免串单。
                if (order.couponCode() == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单未关联优惠券");
                coupons = businessQueryService.coupon(order.couponCode(), order.customerNo()).records();
            }
            case MEMBER_BENEFIT_NOT_RECEIVED -> members = businessQueryService.member(businessNo).records();
            case LOGISTICS_TRACKING_QUERY, LOGISTICS_STATUS_NOT_SYNCED -> {
                // 本轮问物流时先核对订单，再查询物流系统；没有运单也是有效业务结论，不回退到历史工单场景。
                orders = businessQueryService.order(businessNo).records();
                try {
                    logistics = businessQueryService.logisticsByOrder(businessNo).records();
                } catch (BusinessException exception) {
                    if (exception.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) throw exception;
                    logistics = List.of();
                }
            }
            case API_FREQUENT_FAILURE -> apiCalls = businessQueryService.apiCalls(businessNo, null, 100).records();
            case INVOICE_ISSUE_FAILED -> {
                orders = businessQueryService.order(businessNo).records();
                // 发票咨询允许订单尚未签收、尚未创建发票申请；这些都是可解释的业务状态，
                // 不能因为缺少运单或发票记录把整次诊断标记为系统失败。
                try {
                    logistics = businessQueryService.logisticsByOrder(businessNo).records();
                } catch (BusinessException exception) {
                    if (exception.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) throw exception;
                    logistics = List.of();
                }
                try {
                    invoices = businessQueryService.invoiceByOrder(businessNo).records();
                } catch (BusinessException exception) {
                    if (exception.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) throw exception;
                    invoices = List.of();
                }
            }
            case PRODUCT_TRACE_ANOMALY -> traceDetail = traceDataGateway.trace(businessNo);
            case UNKNOWN -> throw new BusinessException(ErrorCode.UNKNOWN_SCENARIO);
        }
        // 每份报告必须绑定当时启用的 SOP，保证后续审计能够还原处置步骤。
        SopRecord sop = scenario == ScenarioType.PRODUCT_TRACE_ANOMALY ? traceSop()
                : businessQueryService.sop(scenario.name()).records().get(0);
        return new DiagnosisContext(scenario, ticketNo, businessNo, LocalDateTime.now(), orders, payments,
                refunds, coupons, members, logistics, apiCalls, invoices, productKnowledge, sop, traceDetail, question);
    }

    private String productSearchQuery(ScenarioType scenario, String question) {
        String focus = switch (scenario) {
            case PRODUCT_INFORMATION_QUERY -> "规格 参数 功能 兼容 防水 续航 材质 尺寸";
            case PRODUCT_USAGE_GUIDANCE -> "使用 安装 连接 配对 操作 保养 清洁 说明书";
            case PRODUCT_TROUBLESHOOTING -> "故障 排查 无法开机 发热 异味 鼓包 冒烟 漏液 漏电 短路 安全 售后";
            default -> "产品资料";
        };
        String relevantQuestion = relevantProductQuestion(scenario, question);
        return relevantQuestion + " " + focus;
    }

    private String relevantProductQuestion(ScenarioType scenario, String question) {
        if (question == null || question.isBlank()) return "";
        String[] keywords = switch (scenario) {
            case PRODUCT_INFORMATION_QUERY -> new String[]{"规格", "参数", "容量", "兼容", "功能", "防水", "续航", "材质", "尺寸", "多大"};
            case PRODUCT_USAGE_GUIDANCE -> new String[]{"怎么用", "如何用", "使用", "安装", "连接", "配对", "操作", "保养", "清洁", "说明"};
            case PRODUCT_TROUBLESHOOTING -> new String[]{"无法", "不能", "故障", "排查", "发热", "过热", "烫手",
                    "异味", "焦味", "鼓包", "膨胀", "冒烟", "起火", "爆炸", "爆裂", "漏液", "漏电",
                    "触电", "进水", "短路", "火花", "报错", "异常", "坏", "售后"};
            default -> new String[0];
        };
        StringBuilder selected = new StringBuilder();
        for (String clause : question.split("[？?。！!；;\\n]+")) {
            for (String keyword : keywords) {
                if (clause.contains(keyword)) {
                    if (!selected.isEmpty()) selected.append(' ');
                    selected.append(clause.trim());
                    break;
                }
            }
        }
        return selected.isEmpty() ? question : selected.toString();
    }


    private SopRecord traceSop() {
        String content = """
                [{"order":"1","action":"核验","tool":"TraceQueryGateway","text":"读取产品、批次与全生命周期事件快照。","ruleExpression":"businessNo != null"},
                 {"order":"2","action":"比对","tool":"TraceRuleHandler","text":"检查质检、仓储、物流、销售与召回状态冲突。","ruleExpression":"anomalyStatus == true"},
                 {"order":"3","action":"处置","tool":"ManualApproval","text":"冻结风险库存并由业务负责人确认召回或补偿动作。","ruleExpression":"riskLevel == HIGH"}]
                """;
        return new SopRecord(0L, ScenarioType.PRODUCT_TRACE_ANOMALY.name(), "产品全链路异常处置",
                "SUPPORT", 1, content, true, LocalDateTime.now());
    }

}
