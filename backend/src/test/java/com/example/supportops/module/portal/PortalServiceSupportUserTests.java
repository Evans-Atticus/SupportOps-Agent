package com.example.supportops.module.portal;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.portal.dao.PortalDAO;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.UserContextRecord;
import com.example.supportops.module.portal.service.PortalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalServiceSupportUserTests {

    @Mock
    private PortalDAO dao;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PortalService service;

    @BeforeEach
    void setUp() {
        service = new PortalService(dao, passwordEncoder);
        when(dao.findContext("admin")).thenReturn(new UserContextRecord(1L, "ADMIN", null));
    }

    @Test
    void deletesUnreferencedSupportUserAndWritesAudit() {
        when(dao.supportUserReferenceCount(8L)).thenReturn(0L);
        when(dao.deleteSupportUser(8L)).thenReturn(true);

        service.deleteSupportUser("admin", 8L, "req-1");

        verify(dao).deleteSupportUser(8L);
        verify(dao).insertAudit(1L, "SUPPORT_USER_DELETE", "8",
                "管理员删除无业务关联的客服账号", "req-1");
    }

    @Test
    void refusesToDeleteSupportUserWithBusinessHistory() {
        when(dao.supportUserReferenceCount(8L)).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteSupportUser("admin", 8L, "req-2"));

        assertEquals(ErrorCode.RESOURCE_IN_USE, error.getErrorCode());
        verify(dao, never()).deleteSupportUser(8L);
    }

    @Test
    void offboardsSupportUserAndReturnsActiveConversationsToQueue() {
        when(dao.releaseActiveConversations(8L)).thenReturn(3);
        when(dao.disableSupportUser(8L)).thenReturn(true);

        var result = service.offboardSupportUser("admin", 8L, "req-3");

        assertEquals("DISABLED", result.status());
        assertEquals(3, result.reassignedConversations());
        verify(dao).insertAudit(1L, "SUPPORT_USER_OFFBOARD", "8",
                "账号已禁用，回收待处理会话=3", "req-3");
    }
}
