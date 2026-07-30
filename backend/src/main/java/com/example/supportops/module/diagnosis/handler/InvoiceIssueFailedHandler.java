package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.InvoiceRecord;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 发票开具失败：先校验订单资格，再检查不同发票类型的必填字段。 */
@Component
public class InvoiceIssueFailedHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() { return ScenarioType.INVOICE_ISSUE_FAILED; }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        OrderRecord order = context.orders().get(0);
        List<DiagnosisEvidence> evidences = new ArrayList<>();
        evidences.add(HandlerSupport.evidence("biz_orders", order.id(), "order_status", "订单状态",
                order.orderStatus(), "用于判断订单是否完成收货"));

        if (!received(order, context.logistics())) {
            context.logistics().stream().findFirst().ifPresent(logistics -> evidences.add(
                    HandlerSupport.evidence("logistics_records", logistics.id(), "logistics_status", "物流状态",
                            logistics.logisticsStatus(), logistics.statusDescription())));
            return result(InvoiceConsultationStatus.WAITING_RECEIPT,
                    "订单尚未签收，暂不具备开票条件",
                    "等待订单签收后继续处理发票申请",
                    "当前订单尚未签收，暂不具备开票条件。订单签收后，系统将继续处理您的发票申请。",
                    evidences);
        }

        if (context.invoices().isEmpty()) {
            return result(InvoiceConsultationStatus.NEEDS_INFORMATION,
                    "订单已签收，但尚未查询到发票申请资料",
                    "请客户补充发票抬头、发票类型、企业税号（如适用）和接收邮箱",
                    "当前订单已签收，但开票资料尚不完整。请补充发票抬头、发票类型、企业税号（如适用）和接收邮箱后再提交。",
                    evidences);
        }

        InvoiceRecord invoice = context.invoices().get(0);
        evidences.add(HandlerSupport.evidence("invoice_applications", invoice.id(), "issue_status", "开票状态",
                invoice.issueStatus(), "失败码：" + invoice.failureCode()));
        // 企业发票比个人发票多要求税号；所有类型都要求抬头和接收邮箱。
        List<String> missing = new ArrayList<>();
        if (invoice.title() == null || invoice.title().isBlank()) missing.add("发票抬头");
        // 税号只对企业发票强制要求，个人发票不会命中此规则。
        if ("COMPANY".equals(invoice.invoiceType()) && (invoice.taxNo() == null || invoice.taxNo().isBlank())) missing.add("企业税号");
        if (invoice.emailMasked() == null || invoice.emailMasked().isBlank()) missing.add("接收邮箱");
        evidences.add(HandlerSupport.evidence("invoice_applications", invoice.id(), "tax_no", "企业税号",
                invoice.taxNo(), "企业发票税号不能为空"));

        if (!missing.isEmpty()) {
            String fields = String.join("、", missing);
            return result(InvoiceConsultationStatus.NEEDS_INFORMATION,
                    "开票资料缺失：" + fields,
                    "请客户补充缺失资料后重新提交",
                    "当前开票资料不完整，请补充" + fields + "后重新提交。",
                    evidences);
        }

        InvoiceConsultationStatus status = resolveStatus(invoice);
        return switch (status) {
            case ISSUING -> result(status, "发票正在开具", "等待开票平台返回结果",
                    "您的发票正在开具中，请稍后查看开票结果。", evidences);
            case ISSUED -> result(status, "发票已开具成功", "引导客户查看或下载发票",
                    "您的发票已开具成功，请前往订单详情查看或下载。", evidences);
            case MANUAL_REVIEW -> result(status, "发票申请需要人工审核", "转交财务人员审核",
                    "您的发票申请正在人工审核中，审核完成后将更新处理结果。", evidences);
            case REJECTED -> result(status, "发票申请已被拒绝", "告知客户拒绝原因并引导核对资料",
                    "当前发票申请未通过审核，请核对开票资料或联系人工客服进一步处理。", evidences);
            case FAILED_RETRYABLE -> result(status,
                    "开票平台暂时处理失败" + failureSuffix(invoice.failureCode()),
                    "保留申请并等待系统重试；多次失败后转人工处理",
                    "开票平台暂时处理失败，系统将保留申请并可再次重试，请稍后查看处理结果。", evidences);
            // WAITING_RECEIPT 和 NEEDS_INFORMATION 已在前置规则中返回。
            case WAITING_RECEIPT, NEEDS_INFORMATION -> throw new IllegalStateException("发票状态判断顺序异常");
        };
    }

    private boolean received(OrderRecord order, List<LogisticsRecord> logistics) {
        if (!logistics.isEmpty()) {
            return logistics.stream().anyMatch(record -> "DELIVERED".equalsIgnoreCase(record.logisticsStatus()));
        }
        // 部分订单没有独立运单快照，订单系统的 COMPLETED 作为已完成收货的可信兜底状态。
        return "COMPLETED".equalsIgnoreCase(order.orderStatus());
    }

    private InvoiceConsultationStatus resolveStatus(InvoiceRecord invoice) {
        String qualification = normalized(invoice.qualificationStatus());
        String issue = normalized(invoice.issueStatus());
        if ("REJECTED".equals(qualification) || "REJECTED".equals(issue)) return InvoiceConsultationStatus.REJECTED;
        if ("MANUAL_REVIEW".equals(qualification) || "UNDER_REVIEW".equals(qualification)
                || "MANUAL_REVIEW".equals(issue) || "UNDER_REVIEW".equals(issue)) {
            return InvoiceConsultationStatus.MANUAL_REVIEW;
        }
        return switch (issue) {
            case "ISSUED", "SUCCESS", "SUCCEEDED" -> InvoiceConsultationStatus.ISSUED;
            case "ISSUING", "PROCESSING", "PENDING" -> InvoiceConsultationStatus.ISSUING;
            default -> InvoiceConsultationStatus.FAILED_RETRYABLE;
        };
    }

    private DiagnosisResult result(InvoiceConsultationStatus status, String conclusion, String suggestion,
                                   String reply, List<DiagnosisEvidence> evidences) {
        return new DiagnosisResult("发票咨询状态判断完成",
                "状态：" + status.name() + "（" + status.label + "）；" + conclusion,
                suggestion, reply, 0.99, List.copyOf(evidences));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String failureSuffix(String failureCode) {
        return failureCode == null || failureCode.isBlank() ? "" : "（失败码：" + failureCode + "）";
    }

    private enum InvoiceConsultationStatus {
        WAITING_RECEIPT("等待签收"),
        NEEDS_INFORMATION("资料不完整"),
        ISSUING("正在开票"),
        ISSUED("开票成功"),
        FAILED_RETRYABLE("开票平台暂时失败，可重试"),
        MANUAL_REVIEW("需要财务审核"),
        REJECTED("确认不能开票");

        private final String label;

        InvoiceConsultationStatus(String label) {
            this.label = label;
        }
    }
}
