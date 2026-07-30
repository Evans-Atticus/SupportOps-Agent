package com.example.supportops.module.system.model.vo;

import java.util.List;

public record HealthVO(String status, int javaVersion, List<String> activeProfiles) {
}
