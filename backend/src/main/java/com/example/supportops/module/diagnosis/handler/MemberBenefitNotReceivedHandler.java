package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.MemberRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.List;

/** 会员权益未到账：先验证会员资格，再定位失败的权益发放记录。 */
@Component
public class MemberBenefitNotReceivedHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() { return ScenarioType.MEMBER_BENEFIT_NOT_RECEIVED; }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        MemberRecord account = context.members().get(0);
        // LEFT JOIN 可能返回多条权益记录，只选取明确失败的一条作为根因证据。
        MemberRecord failed = context.members().stream()
                .filter(record -> "FAILED".equals(record.grantStatus()))
                // 没有失败记录时保留账户行，使后续能够返回“条件不完整”而不是空指针。
                .findFirst().orElse(account);
        // 账户状态和诊断时间必须同时落在会员有效期内。
        boolean active = "ACTIVE".equals(account.memberStatus())
                && !context.diagnosedAt().isBefore(account.validFrom())
                && !context.diagnosedAt().isAfter(account.validUntil());
        boolean grantFailed = "FAILED".equals(failed.grantStatus());
        List<DiagnosisEvidence> evidence = List.of(
                HandlerSupport.evidence("member_accounts", account.memberId(), "member_status", "会员状态", account.memberStatus(),
                        "有效期：" + account.validFrom() + " 至 " + account.validUntil()),
                new DiagnosisEvidence("member_benefit_records", null, "grant_status", "权益发放状态",
                        String.valueOf(failed.grantStatus()), "权益：" + failed.benefitName() + "，失败码：" + failed.failureCode(), 1.0)
        );
        return new DiagnosisResult("会员资格与权益发放记录核对完成",
                active && grantFailed ? "会员有效，但权益发放事件失败" : "未同时满足会员有效与权益发放失败条件",
                active && grantFailed ? "按权益编号提交幂等补发，并保留原失败记录" : "转会员运营复核资格",
                "您好，您的会员资格有效，相关权益发放记录出现异常，我们已按流程提交核查。",
                active && grantFailed ? 0.98 : 0.68, evidence);
    }
}
