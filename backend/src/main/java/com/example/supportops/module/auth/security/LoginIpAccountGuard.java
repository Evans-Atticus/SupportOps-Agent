package com.example.supportops.module.auth.security;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例登录 IP 防滥用保护：一小时窗口内，一个 IP 最多成功登录指定数量的不同账号。
 * 只保存在内存中，不把原始 IP 写入数据库或日志；后端重启后窗口自然清空。
 */
@Component
public class LoginIpAccountGuard {
    private final Map<String, Map<String, Instant>> accountsByIp = new ConcurrentHashMap<>();
    private final int maximumAccounts;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public LoginIpAccountGuard(
            @Value("${supportops.auth.max-accounts-per-ip-per-hour:5}") int maximumAccounts) {
        this(maximumAccounts, Duration.ofHours(1), Clock.systemUTC());
    }

    LoginIpAccountGuard(int maximumAccounts, Duration window, Clock clock) {
        this.maximumAccounts = maximumAccounts;
        this.window = window;
        this.clock = clock;
    }

    public void checkAndRecord(String clientIp, String username) {
        String safeIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        Map<String, Instant> accounts = accountsByIp.computeIfAbsent(safeIp, ignored -> new HashMap<>());
        synchronized (accounts) {
            Instant cutoff = clock.instant().minus(window);
            accounts.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            if (!accounts.containsKey(username) && accounts.size() >= maximumAccounts) {
                throw new BusinessException(ErrorCode.LOGIN_IP_ACCOUNT_LIMIT_EXCEEDED);
            }
            accounts.put(username, clock.instant());
        }
    }
}
