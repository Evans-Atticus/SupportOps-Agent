package com.example.supportops.module.diagnosis.persistence;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.business.model.query.SopRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.DiagnosisStep;
import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.diagnosis.model.vo.DiagnosisHistoryVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 诊断任务、步骤、报告和证据聚合的数据访问对象。 */
@Repository
public class DiagnosisRepository {
    private final JdbcTemplate jdbcTemplate;

    public DiagnosisRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findByIdempotency(Long userId, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return jdbcTemplate.query("SELECT id FROM diagnosis_tasks WHERE requested_by=? AND idempotency_key=?",
                (rs, rowNum) -> rs.getLong(1), userId, key).stream().findFirst();
    }

    /** 无显式幂等键时，短时间重复提交同一工单直接复用已完成结果。 */
    public Optional<Long> findRecentReusable(Long userId, Long ticketId, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return jdbcTemplate.query("""
                SELECT id FROM diagnosis_tasks
                 WHERE requested_by=? AND ticket_id=?
                   AND status IN ('SUCCESS','DEGRADED_SUCCESS') AND created_at>=?
                 ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> rs.getLong(1), userId, ticketId, since).stream().findFirst();
    }

    public int countCreatedToday(Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM diagnosis_tasks
                 WHERE requested_by=? AND created_at>=CURRENT_DATE AND created_at<CURRENT_DATE + INTERVAL 1 DAY
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    public int countCreatedSince(Long userId, LocalDateTime since) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM diagnosis_tasks WHERE requested_by=? AND created_at>=?",
                Integer.class, userId, since);
        return count == null ? 0 : count;
    }

    public int countActive(Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM diagnosis_tasks
                 WHERE requested_by=?
                   AND status IN ('PENDING','UNDERSTANDING','QUERYING','DIAGNOSING','GENERATING_REPLY')
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** 创建 PENDING 主任务和前端需要展示的五个步骤。 */
    @Transactional
    public long createTask(Long ticketId, Long userId, String idempotencyKey) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO diagnosis_tasks
                      (ticket_id, requested_by, idempotency_key, status, degraded, model_call_count)
                    VALUES (?, ?, ?, 'PENDING', 0, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ticketId);
            statement.setLong(2, userId);
            statement.setString(3, blankToNull(idempotencyKey));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new BusinessException(ErrorCode.DATABASE_ERROR, "诊断任务主键生成失败");
        long id = key.longValue();
        insertStep(id, 1, "UNDERSTAND_TICKET", "AI 理解工单");
        insertStep(id, 2, "QUERY_BUSINESS", "查询业务快照");
        insertStep(id, 3, "DIAGNOSE_RULES", "执行确定性规则");
        insertStep(id, 4, "GENERATE_REPLY", "生成客服回复");
        insertStep(id, 5, "BUILD_REPORT", "保存报告和证据");
        return id;
    }

    public void startTask(long diagnosisId) {
        jdbcTemplate.update("UPDATE diagnosis_tasks SET status='UNDERSTANDING', started_at=NOW(3) WHERE id=?", diagnosisId);
    }

    public void updateStatus(long diagnosisId, DiagnosisStatus status, ScenarioType scenario) {
        jdbcTemplate.update("UPDATE diagnosis_tasks SET status=?, scenario_type=COALESCE(?, scenario_type) WHERE id=?",
                status.name(), scenario == null ? null : scenario.name(), diagnosisId);
    }

    public void startStep(long diagnosisId, String code) {
        jdbcTemplate.update("""
                UPDATE diagnosis_steps SET status='RUNNING', started_at=NOW(3), detail_message=NULL
                 WHERE diagnosis_id=? AND step_code=?
                """, diagnosisId, code);
    }

    public void finishStep(long diagnosisId, String code, long durationMs, String detail) {
        jdbcTemplate.update("""
                UPDATE diagnosis_steps SET status='SUCCESS', duration_ms=?, detail_message=?, finished_at=NOW(3)
                 WHERE diagnosis_id=? AND step_code=?
                """, durationMs, abbreviate(detail, 500), diagnosisId, code);
    }

    public void degradeStep(long diagnosisId, String code, long durationMs, String detail) {
        jdbcTemplate.update("""
                UPDATE diagnosis_steps SET status='DEGRADED', duration_ms=?, detail_message=?, finished_at=NOW(3)
                 WHERE diagnosis_id=? AND step_code=?
                """, durationMs, abbreviate(detail, 500), diagnosisId, code);
    }

    public boolean reserveModelCall(long diagnosisId, int maximum) {
        return jdbcTemplate.update("""
                UPDATE diagnosis_tasks SET model_call_count=model_call_count+1
                 WHERE id=? AND model_call_count<?
                """, diagnosisId, maximum) == 1;
    }

    /** 规则报告已生成后，以一个事务保存报告、证据并切换最终状态。 */
    @Transactional
    public void complete(long diagnosisId, ScenarioType scenario, String reportTitle,
                         DiagnosisResult result, SopRecord sop,
                         String customerReply, boolean degraded, String degradationCode) {
        jdbcTemplate.update("""
                INSERT INTO diagnosis_reports
                  (diagnosis_id, title, summary, conclusion, internal_suggestion, customer_reply, sop_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, diagnosisId, reportTitle, result.summary(), result.conclusion(),
                result.internalSuggestion(), customerReply, sop.id());
        for (int i = 0; i < result.evidences().size(); i++) {
            DiagnosisEvidence evidence = result.evidences().get(i);
            jdbcTemplate.update("""
                    INSERT INTO diagnosis_evidences
                      (diagnosis_id, evidence_order, source_table, source_record_id, source_field,
                       label, evidence_value, description, confidence)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, diagnosisId, i + 1, evidence.source(), evidence.sourceRecordId(), evidence.field(),
                    evidence.label(), evidence.value(), evidence.description(), evidence.confidence());
        }
        DiagnosisStatus finalStatus = degraded ? DiagnosisStatus.DEGRADED_SUCCESS : DiagnosisStatus.SUCCESS;
        jdbcTemplate.update("""
                UPDATE diagnosis_tasks
                   SET scenario_type=?, status=?, confidence=?, degraded=?, error_code=?, finished_at=NOW(3)
                 WHERE id=? AND status<>'DISCARDED'
                """, scenario.name(), finalStatus.name(), result.confidence(), degraded,
                degradationCode, diagnosisId);
    }

    public void fail(long diagnosisId, ErrorCode errorCode, String message) {
        jdbcTemplate.update("""
                UPDATE diagnosis_tasks SET status='FAILED', error_code=?, error_message=?, finished_at=NOW(3)
                 WHERE id=? AND status<>'DISCARDED'
                """, errorCode.name(), abbreviate(message, 500), diagnosisId);
    }

    public void failRunningSteps(long diagnosisId, String detail) {
        jdbcTemplate.update("""
                UPDATE diagnosis_steps SET status='FAILED', detail_message=?, finished_at=NOW(3)
                 WHERE diagnosis_id=? AND status='RUNNING'
                """, abbreviate(detail, 500), diagnosisId);
    }

    /** LEFT JOIN 允许任务尚未生成报告时也能被轮询查询。 */
    public PersistedDiagnosis getRequired(long diagnosisId) {
        List<PersistedDiagnosis> roots = jdbcTemplate.query("""
                SELECT t.id, t.status, t.scenario_type, t.confidence, t.degraded, t.error_code, t.error_message,
                       r.title, r.summary, r.conclusion, r.internal_suggestion, r.customer_reply,
                       s.title AS sop_title, s.audience AS sop_audience, s.content_json
                  FROM diagnosis_tasks t
                  LEFT JOIN diagnosis_reports r ON r.diagnosis_id=t.id
                  LEFT JOIN sop_definitions s ON s.id=r.sop_id
                 WHERE t.id=?
                """, (rs, rowNum) -> new PersistedDiagnosis(
                rs.getLong("id"), DiagnosisStatus.valueOf(rs.getString("status")), enumOrNull(rs.getString("scenario_type")),
                rs.getObject("confidence", Double.class), rs.getBoolean("degraded"), rs.getString("error_code"),
                rs.getString("error_message"), rs.getString("title"), rs.getString("summary"),
                rs.getString("conclusion"), rs.getString("internal_suggestion"), rs.getString("customer_reply"),
                rs.getString("sop_title"), rs.getString("sop_audience"), rs.getString("content_json"),
                loadSteps(diagnosisId), loadEvidences(diagnosisId)), diagnosisId);
        return roots.stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND));
    }

    /** 只返回当前用户的最近任务，既支持历史侧栏，也避免跨账号读取诊断结果。 */
    public List<DiagnosisHistoryVO> listRecent(long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT d.id, t.ticket_no, t.business_no, t.description, d.status, d.scenario_type,
                       COALESCE(r.title, t.description) AS title, d.created_at, d.finished_at
                  FROM diagnosis_tasks d
                  JOIN tickets t ON t.id=d.ticket_id
                  LEFT JOIN diagnosis_reports r ON r.diagnosis_id=d.id
                 WHERE d.requested_by=?
                 ORDER BY d.created_at DESC LIMIT ?
                """, (rs, rowNum) -> new DiagnosisHistoryVO(
                rs.getLong("id"), rs.getString("ticket_no"), rs.getString("business_no"),
                rs.getString("description"), DiagnosisStatus.valueOf(rs.getString("status")),
                enumOrNull(rs.getString("scenario_type")), rs.getString("title"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime()),
                userId, limit);
    }

    public boolean belongsTo(long diagnosisId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM diagnosis_tasks WHERE id=? AND requested_by=?", Integer.class,
                diagnosisId, userId);
        return count != null && count > 0;
    }

    public long insertAttachment(long diagnosisId, String originalName, String contentType,
                                 long sizeBytes, byte[] content) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO diagnosis_attachments
                      (diagnosis_id, original_name, content_type, size_bytes, file_content)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, diagnosisId);
            statement.setString(2, originalName);
            statement.setString(3, contentType);
            statement.setLong(4, sizeBytes);
            statement.setBytes(5, content);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new BusinessException(ErrorCode.DATABASE_ERROR, "诊断附件主键生成失败");
        return key.longValue();
    }

    /** 采纳只记录审计时间，不修改已完成诊断的事实状态。 */
    public void apply(long diagnosisId) {
        int changed = jdbcTemplate.update("""
                UPDATE diagnosis_reports r
                  JOIN diagnosis_tasks d ON d.id=r.diagnosis_id
                   SET r.adopted_at=NOW(3), r.discarded_at=NULL
                 WHERE r.diagnosis_id=? AND d.status IN ('SUCCESS','DEGRADED_SUCCESS')
                """, diagnosisId);
        if (changed == 0) throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    /** 丢弃终止任务并留下报告审计时间；已失败或已丢弃任务不能重复操作。 */
    @Transactional
    public void discard(long diagnosisId) {
        int changed = jdbcTemplate.update("""
                UPDATE diagnosis_tasks SET status='DISCARDED', finished_at=COALESCE(finished_at, NOW(3))
                 WHERE id=? AND status NOT IN ('FAILED','DISCARDED')
                """, diagnosisId);
        if (changed == 0) throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        jdbcTemplate.update("UPDATE diagnosis_reports SET discarded_at=NOW(3), adopted_at=NULL WHERE diagnosis_id=?",
                diagnosisId);
    }

    private void insertStep(long diagnosisId, int order, String code, String title) {
        jdbcTemplate.update("""
                INSERT INTO diagnosis_steps (diagnosis_id, step_order, step_code, title, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, diagnosisId, order, code, title);
    }

    private List<DiagnosisStep> loadSteps(long diagnosisId) {
        return jdbcTemplate.query("""
                SELECT step_code, title, status, duration_ms, detail_message
                  FROM diagnosis_steps WHERE diagnosis_id=? ORDER BY step_order
                """, (rs, rowNum) -> new DiagnosisStep(rs.getString("step_code"), rs.getString("title"),
                rs.getString("status"), rs.getObject("duration_ms") == null ? 0 : rs.getLong("duration_ms"),
                rs.getString("detail_message")), diagnosisId);
    }

    private List<DiagnosisEvidence> loadEvidences(long diagnosisId) {
        return jdbcTemplate.query("""
                SELECT source_table, source_record_id, source_field, label, evidence_value, description, confidence
                  FROM diagnosis_evidences WHERE diagnosis_id=? ORDER BY evidence_order
                """, (rs, rowNum) -> new DiagnosisEvidence(rs.getString("source_table"),
                rs.getObject("source_record_id", Long.class), rs.getString("source_field"), rs.getString("label"),
                rs.getString("evidence_value"), rs.getString("description"), rs.getDouble("confidence")), diagnosisId);
    }

    private ScenarioType enumOrNull(String value) {
        return value == null ? null : ScenarioType.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String abbreviate(String value, int maximum) {
        if (value == null || value.length() <= maximum) return value;
        return value.substring(0, maximum);
    }
}
