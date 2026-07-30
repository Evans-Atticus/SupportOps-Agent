package com.example.supportops.module.business.model.vo;

import com.example.supportops.module.business.model.query.BusinessQueryRecord;

import java.util.List;

public record BusinessSnapshotVO<T extends BusinessQueryRecord>(String category, String businessKey,
                                                                 List<T> records) {
}
