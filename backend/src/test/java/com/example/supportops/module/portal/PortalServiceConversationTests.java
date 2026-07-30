package com.example.supportops.module.portal;

import com.example.supportops.module.portal.dao.PortalDAO;
import com.example.supportops.module.portal.model.dto.HandoffRequestDTO;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationContextRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.UserContextRecord;
import com.example.supportops.module.portal.service.PortalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalServiceConversationTests {

    @Mock
    private PortalDAO dao;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PortalService service;

    @BeforeEach
    void setUp() {
        service = new PortalService(dao, passwordEncoder);
        lenient().when(dao.findContext("support01"))
                .thenReturn(new UserContextRecord(2L, "SUPPORT_AGENT", null));
    }

    @Test
    void generatesSuggestionsFromEachConversationBusinessContext() {
        stubConversation("payment", "PAYMENT_SUCCESS_ORDER_PENDING", "EXECUTING");
        stubConversation("logistics", "LOGISTICS_STATUS_NOT_SYNCED", null);
        stubConversation("cancelled", "ORDER_CANCELLED_BUT_CHARGED", "UNDER_REVIEW");

        String payment = service.conversation("support01", "payment").suggestedReply();
        String logistics = service.conversation("support01", "logistics").suggestedReply();
        String cancelled = service.conversation("support01", "cancelled").suggestedReply();

        assertTrue(payment.contains("1—3 个工作日"));
        assertTrue(logistics.contains("签收凭证"));
        assertTrue(cancelled.contains("审核处理中"));
        assertNotEquals(payment, logistics);
        assertNotEquals(payment, cancelled);
        assertNotEquals(logistics, cancelled);
    }

    @Test
    void doesNotSuggestForConversationTransferredDirectlyToHuman() {
        var record = new ConversationContextRecord(
                7L, "direct", "演示客户", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "ORIGINAL", "WAITING_AGENT",
                null, "PAYMENT_SUCCESS_ORDER_PENDING", "APPROVED");
        when(dao.findAgentConversation("direct", 2L)).thenReturn(record);
        when(dao.selectConversationAttachments(7L)).thenReturn(List.of());
        when(dao.selectConversationMessages(7L)).thenReturn(List.of());

        assertNull(service.conversation("support01", "direct").suggestedReply());
    }

    @Test
    void usesGroundedDiagnosisReplyInsteadOfScenarioTemplate() {
        var record = new ConversationContextRecord(
                8L, "grounded", "演示客户", "TK-1", "O-1",
                new BigDecimal("559.00"), new BigDecimal("559.00"), "ORIGINAL", "WAITING_AGENT",
                101L, "LOGISTICS_TRACKING_QUERY", null,
                "包裹目前位于苏州市工业园区金鸡湖街道，预计今天 18:00 前送达。");
        when(dao.findAgentConversation("grounded", 2L)).thenReturn(record);
        when(dao.selectConversationAttachments(8L)).thenReturn(List.of());
        when(dao.selectConversationMessages(8L)).thenReturn(List.of());

        assertEquals("包裹目前位于苏州市工业园区金鸡湖街道，预计今天 18:00 前送达。",
                service.conversation("support01", "grounded").suggestedReply());
    }

    @Test
    void putsNewHumanHandoffIntoSharedWaitingQueue() {
        when(dao.findContext("customer01"))
                .thenReturn(new UserContextRecord(9L, "CUSTOMER", 19L));
        when(dao.createHandoffConversation(anyString(), eq(19L), isNull(),
                eq("TK-1"), eq("O-1"), isNull(), eq("请转人工"))).thenReturn(88L);

        var result = service.requestHandoff("customer01",
                new HandoffRequestDTO("请转人工", "TK-1", "O-1", null, null));

        assertTrue(result.conversationNo().startsWith("CONV-"));
        assertTrue("WAITING_AGENT".equals(result.status()));
        assertNull(result.assignedAgent());
        verify(dao).insertCustomerMessage(88L, 9L, "请转人工");
    }

    private void stubConversation(String conversationNo, String scenarioHint, String refundStatus) {
        var record = new ConversationContextRecord(
                1L, conversationNo, "测试客户", "TK-1", "O-1",
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                "ORIGINAL", "AGENT_SERVING", 99L, scenarioHint, refundStatus);
        when(dao.findAgentConversation(conversationNo, 2L)).thenReturn(record);
        when(dao.selectConversationAttachments(1L)).thenReturn(List.of());
        when(dao.selectConversationMessages(1L)).thenReturn(List.of());
    }
}
