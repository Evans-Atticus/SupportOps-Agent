package com.example.supportops.module.ai;

import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.ai.audit.AiErrorMapper;
import com.example.supportops.module.ai.mock.MockCustomerReplyAiService;
import com.example.supportops.module.ai.mock.MockTicketUnderstandingAiService;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockAiServicesTests {
    private final MockTicketUnderstandingAiService understanding = new MockTicketUnderstandingAiService();

    @Test
    void recognizesAllSevenScenarios() {
        assertScenario("支付成功但订单仍显示待支付", ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING);
        assertScenario("订单取消后仍然扣款且退款未到账", ScenarioType.ORDER_CANCELLED_BUT_CHARGED);
        assertScenario("优惠券结算时无法使用", ScenarioType.COUPON_UNAVAILABLE);
        assertScenario("会员每月权益一直没有到账", ScenarioType.MEMBER_BENEFIT_NOT_RECEIVED);
        assertScenario("我的包裹到哪里了，预计什么时候送达", ScenarioType.LOGISTICS_TRACKING_QUERY);
        assertScenario("API 接口频繁返回 503", ScenarioType.API_FREQUENT_FAILURE);
        assertScenario("企业发票开票失败", ScenarioType.INVOICE_ISSUE_FAILED);
    }

    @Test
    void unknownTextReturnsLowConfidenceAndMissingInformation() {
        var result = understanding.understand("帮我看一下");
        assertEquals(ScenarioType.UNKNOWN, result.scenarioType());
        assertTrue(result.confidence() < 0.55);
        assertFalse(result.missingInformation().isEmpty());
    }

    @Test
    void understandsMultipleQuestionsInsteadOfDroppingTheSecondOne() {
        var result = understanding.understand("价格和物流都帮我查一下");
        assertEquals(List.of(ScenarioType.ORDER_INFORMATION_QUERY, ScenarioType.LOGISTICS_TRACKING_QUERY),
                result.scenarioTypes());
    }

    @Test
    void replyUsesOnlyVerifiedContextLines() {
        var draft = new MockCustomerReplyAiService().generate("""
                结论：订单金额未达到优惠券门槛
                对客可见下一步：调整订单后重试
                """);
        assertTrue(draft.content().contains("订单金额未达到优惠券门槛"));
        assertTrue(draft.content().contains("调整订单后重试"));
    }

    @Test
    void mapsTimeoutAndRateLimitErrors() {
        assertEquals(ErrorCode.AI_TIMEOUT, AiErrorMapper.code(new SocketTimeoutException()));
        assertEquals(ErrorCode.AI_RATE_LIMITED, AiErrorMapper.code(new RuntimeException("HTTP 429")));
    }

    @Test
    void mapsQuotaErrorsBeforeGenericHttp429() {
        assertEquals(ErrorCode.AI_QUOTA_EXHAUSTED,
                AiErrorMapper.code(new RuntimeException("HTTP 429 insufficient_quota: account balance exhausted")));
        assertEquals(ErrorCode.AI_QUOTA_EXHAUSTED,
                AiErrorMapper.code(new RuntimeException("Arrearage: 账户余额不足")));
    }

    @Test
    void quotaExhaustionOpensCircuitAndSkipsFollowingModelCalls() {
        var understandingService = mock(com.example.supportops.module.ai.understanding.TicketUnderstandingAiService.class);
        var replyService = mock(com.example.supportops.module.ai.reply.CustomerReplyAiService.class);
        var repository = mock(com.example.supportops.module.diagnosis.persistence.DiagnosisRepository.class);
        var logs = mock(com.example.supportops.module.ai.audit.ModelCallLogRepository.class);
        var validator = mock(jakarta.validation.Validator.class);
        when(repository.reserveModelCall(1L, 2)).thenReturn(true);
        when(logs.start(1L, "request-1", "UNDERSTANDING", "OPENAI_COMPATIBLE", "glm-5.2")).thenReturn(10L);
        when(understandingService.understand("测试工单"))
                .thenThrow(new RuntimeException("HTTP 429 insufficient_quota"));
        var service = new AiInvocationService(understandingService, replyService, repository, logs,
                validator, "real", 2, "glm-5.2");

        assertEquals(ErrorCode.AI_QUOTA_EXHAUSTED,
                assertThrows(AiCallException.class,
                        () -> service.understand(1L, "request-1", "测试工单")).errorCode());
        assertEquals(ErrorCode.AI_QUOTA_EXHAUSTED,
                assertThrows(AiCallException.class,
                        () -> service.understand(1L, "request-2", "第二次调用")).errorCode());

        verify(understandingService, times(1)).understand("测试工单");
        verify(repository, times(1)).reserveModelCall(1L, 2);
    }

    @Test
    void retriesCustomerReplyOnceWhenStructuredOutputCannotBeParsed() {
        var understandingService = mock(com.example.supportops.module.ai.understanding.TicketUnderstandingAiService.class);
        var replyService = mock(com.example.supportops.module.ai.reply.CustomerReplyAiService.class);
        var repository = mock(com.example.supportops.module.diagnosis.persistence.DiagnosisRepository.class);
        var logs = mock(com.example.supportops.module.ai.audit.ModelCallLogRepository.class);
        var validator = mock(jakarta.validation.Validator.class);
        when(repository.reserveModelCall(9L, 3)).thenReturn(true);
        when(logs.start(eq(9L), eq("request-retry"), anyString(), eq("OPENAI_COMPATIBLE"), eq("glm-5.2")))
                .thenReturn(20L, 21L);
        when(replyService.generate(anyString()))
                .thenThrow(new RuntimeException("json parse failed"))
                .thenReturn(new com.example.supportops.module.ai.reply.ReplyDraft("已为您核对完成。", "professional"));
        when(validator.validate(any())).thenReturn(java.util.Set.of());
        var service = new AiInvocationService(understandingService, replyService, repository, logs,
                validator, "real", 3, "glm-5.2");

        var result = service.generateReply(9L, "request-retry", "verified context");

        assertEquals("已为您核对完成。", result.content());
        verify(replyService, times(2)).generate(anyString());
        verify(repository, times(2)).reserveModelCall(9L, 3);
        verify(logs).failure(eq(20L), eq(ErrorCode.AI_RESPONSE_PARSE_FAILED.name()), anyLong());
    }

    private void assertScenario(String text, ScenarioType expected) {
        assertEquals(expected, understanding.understand(text).scenarioType());
    }
}
