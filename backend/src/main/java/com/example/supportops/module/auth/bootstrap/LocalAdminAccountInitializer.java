package com.example.supportops.module.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本机 real 环境管理员初始化器。
 * 历史 demo 账号会原地升级，以保留已有工单和诊断记录的外键关系。
 */
@Component
@Profile("real")
@ConditionalOnProperty(prefix = "supportops.local-admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalAdminAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalAdminAccountInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public LocalAdminAccountInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String encodedPassword = passwordEncoder.encode("12345678l");
        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM support_users WHERE username='admin'", Integer.class);
        if (adminCount != null && adminCount > 0) {
            jdbcTemplate.update("""
                    UPDATE support_users
                       SET password_hash=?, display_name='系统管理员', role_code='ADMIN',
                           status='ACTIVE', daily_quota=2147483647
                     WHERE username='admin'
                    """, encodedPassword);
            jdbcTemplate.update("UPDATE support_users SET status='DISABLED' WHERE username='demo'");
        } else {
            int upgraded = jdbcTemplate.update("""
                    UPDATE support_users
                       SET username='admin', password_hash=?, display_name='系统管理员',
                           role_code='ADMIN', status='ACTIVE', daily_quota=2147483647
                     WHERE username='demo'
                    """, encodedPassword);
            if (upgraded == 0) {
                jdbcTemplate.update("""
                        INSERT INTO support_users
                          (username, password_hash, display_name, role_code, status, daily_quota)
                        VALUES ('admin', ?, '系统管理员', 'ADMIN', 'ACTIVE', 2147483647)
                        """, encodedPassword);
            }
        }
        ensureLocalAccount("support01", "客服小李", "SUPPORT_AGENT", 50, encodedPassword);
        ensureLocalAccount("customer01", "演示客户", "CUSTOMER", 10, encodedPassword);
        log.info("Local role accounts are ready: admin, support01 and customer01");
    }

    private void ensureLocalAccount(String username, String displayName, String roleCode,
                                    int dailyQuota, String encodedPassword) {
        jdbcTemplate.update("""
                INSERT INTO support_users
                  (username, password_hash, display_name, role_code, status, daily_quota)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                ON DUPLICATE KEY UPDATE
                  password_hash=VALUES(password_hash),
                  display_name=VALUES(display_name),
                  role_code=VALUES(role_code),
                  status='ACTIVE',
                  daily_quota=VALUES(daily_quota)
                """, username, encodedPassword, displayName, roleCode, dailyQuota);
    }
}
