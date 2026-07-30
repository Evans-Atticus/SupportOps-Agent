package com.example.supportops.module.ai.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;

/** 只保存模型元数据和 Token，不保存完整 Prompt、回复或 API Key。 */
@Repository
public class ModelCallLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public ModelCallLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long start(long diagnosisId, String requestId, String callType, String provider, String modelName) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO model_call_logs
                      (diagnosis_id, request_id, call_type, provider, model_name, call_status)
                    VALUES (?, ?, ?, ?, ?, 'STARTED')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, diagnosisId);
            statement.setString(2, requestId);
            statement.setString(3, callType);
            statement.setString(4, provider);
            statement.setString(5, modelName);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("模型调用日志主键生成失败");
        return key.longValue();
    }

    public void success(long logId, String modelName, Integer inputTokens, Integer outputTokens, long durationMs) {
        jdbcTemplate.update("""
                UPDATE model_call_logs
                   SET call_status='SUCCESS', model_name=COALESCE(?, model_name), input_tokens=?, output_tokens=?, duration_ms=?
                 WHERE id=? AND call_status='STARTED'
                """, modelName, inputTokens, outputTokens, durationMs, logId);
    }

    public void failure(long logId, String errorCode, long durationMs) {
        jdbcTemplate.update("""
                UPDATE model_call_logs SET call_status='FAILED', error_code=?, duration_ms=?
                 WHERE id=? AND call_status IN ('STARTED','SUCCESS')
                """, errorCode, durationMs, logId);
    }
}
