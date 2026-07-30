package com.example.supportops.module.system.service;

import com.example.supportops.module.system.model.vo.HealthVO;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class SystemService {
    private final Environment environment;

    public SystemService(Environment environment) {
        this.environment = environment;
    }

    public HealthVO health() {
        return new HealthVO("UP", Runtime.version().feature(), Arrays.asList(environment.getActiveProfiles()));
    }
}
