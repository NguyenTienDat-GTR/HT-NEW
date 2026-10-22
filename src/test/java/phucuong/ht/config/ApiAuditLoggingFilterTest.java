package phucuong.ht.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.SystemLog;
import phucuong.ht.modules.system.repository.SystemLogRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAuditLoggingFilterTest {

    @Mock private SystemLogRepository systemLogRepository;
    @Mock private AuditScopeResolver auditScopeResolver;
    @Mock private AccountRepository accountRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void truncatesLongRequestMetadataBeforePersistingAuditLog() {
        MockHttpServletRequest request = requestWithOversizedMetadata();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(auditScopeResolver.resolve(any(), any(), any(), any()))
                .thenReturn(AuditScopeContext.builder().build());

        assertDoesNotThrow(() -> filter().doFilter(request, response, (req, res) -> response.setStatus(201)));

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        SystemLog persisted = captor.getValue();
        assertEquals(500, persisted.getRequestPath().length());
        assertEquals(500, persisted.getUserAgent().length());
        assertTrue(persisted.getMessage().length() <= 505);
        assertEquals(201, response.getStatus());
    }

    @Test
    void auditPersistenceFailureDoesNotTurnSuccessfulBusinessResponseIntoServerError() {
        MockHttpServletRequest request = requestWithOversizedMetadata();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(auditScopeResolver.resolve(any(), any(), any(), any()))
                .thenReturn(AuditScopeContext.builder().build());
        doThrow(new RuntimeException("audit unavailable")).when(systemLogRepository).save(any(SystemLog.class));

        assertDoesNotThrow(() -> filter().doFilter(request, response, (req, res) -> response.setStatus(201)));

        assertEquals(201, response.getStatus());
    }

    private ApiAuditLoggingFilter filter() {
        return new ApiAuditLoggingFilter(
                systemLogRepository,
                auditScopeResolver,
                new securityUtils(),
                accountRepository,
                new AuditResourceNamer()
        );
    }

    private MockHttpServletRequest requestWithOversizedMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/training/courses/" + "x".repeat(700));
        request.setRequestURI("/api/training/courses/" + "x".repeat(700));
        request.addHeader("User-Agent", "a".repeat(700));
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
