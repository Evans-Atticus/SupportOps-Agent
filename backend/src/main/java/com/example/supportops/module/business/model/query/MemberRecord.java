package com.example.supportops.module.business.model.query;

import java.time.LocalDateTime;

public record MemberRecord(Long memberId, String memberNo, String customerNo, String memberLevel,
                           String memberStatus, LocalDateTime validFrom, LocalDateTime validUntil,
                           String benefitNo, String benefitCode, String benefitName, String grantStatus,
                           String failureCode, LocalDateTime expectedAt,
                           LocalDateTime grantedAt) implements BusinessQueryRecord {
}
