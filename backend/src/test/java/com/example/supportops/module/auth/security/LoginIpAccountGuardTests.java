package com.example.supportops.module.auth.security;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIpAccountGuardTests {
    @Test
    void allowsFiveDistinctAccountsAndRejectsTheSixth() {
        LoginIpAccountGuard guard = new LoginIpAccountGuard(5, Duration.ofHours(1), Clock.systemUTC());
        for (int index = 1; index <= 5; index++) {
            guard.checkAndRecord("203.0.113.10", "agent" + index);
        }

        // 已计入的账号重复登录不会额外占用名额。
        assertDoesNotThrow(() -> guard.checkAndRecord("203.0.113.10", "agent1"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.checkAndRecord("203.0.113.10", "agent6"));
        assertEquals(ErrorCode.LOGIN_IP_ACCOUNT_LIMIT_EXCEEDED, error.getErrorCode());

        // 其他 IP 拥有独立窗口。
        assertDoesNotThrow(() -> guard.checkAndRecord("203.0.113.11", "agent6"));
    }
}
