package com.example.supportops.module.business.model.query;

import java.time.LocalDateTime;

public record InvoiceRecord(Long id, String invoiceNo, String orderNo, String invoiceType, String title,
                            String taxNo, String emailMasked, String qualificationStatus, String issueStatus,
                            String failureCode, LocalDateTime issuedAt) implements BusinessQueryRecord {
}
