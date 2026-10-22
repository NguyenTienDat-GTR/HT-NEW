package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditTraceabilityTest {
    private static final String LEADER = "5a949e09-ed09-45f1-b819-876bc8c73f85";

    @Test void everyApiPathGetsAResourceSoTheObjectColumnIsNeverEmptyForKnownAreas() {
        assertEquals("leader", ApiAuditLoggingFilter.resolveResource("/api/leaders/" + LEADER));
        assertEquals("analytics", ApiAuditLoggingFilter.resolveResource("/api/analytics/overview"));
        assertEquals("auth", ApiAuditLoggingFilter.resolveResource("/api/auth/me"));
        assertEquals("notification", ApiAuditLoggingFilter.resolveResource("/api/system/notifications/unread-count"));
        assertEquals("leader_level", ApiAuditLoggingFilter.resolveResource("/api/leader-levels/options"));
        assertEquals("participation", ApiAuditLoggingFilter.resolveResource("/api/training/registrations/abc1/withdraw"));
        assertEquals("certificate_config", ApiAuditLoggingFilter.resolveResource("/api/certificates/config/templates"));
        assertEquals("something_new", ApiAuditLoggingFilter.resolveResource("/api/something-new/list"));
    }

    @Test void resourceIdIsARealIdentifierNotTheLastPathWord() {
        assertEquals(LEADER, ApiAuditLoggingFilter.extractResourceId("/api/leaders/" + LEADER, "leader"));
        assertEquals(LEADER, ApiAuditLoggingFilter.extractResourceId("/api/training/courses/" + LEADER + "/score-lock", "course"));
        assertEquals("CC-2026-0001", ApiAuditLoggingFilter.extractResourceId("/api/certificates/CC-2026-0001/revoke", "certificate"));
        assertNull(ApiAuditLoggingFilter.extractResourceId("/api/auth/me", "auth"));
        assertNull(ApiAuditLoggingFilter.extractResourceId("/api/analytics/filter-options", "analytics"));
        assertNull(ApiAuditLoggingFilter.extractResourceId("/api/leader-levels/options", "leader_level"));
        assertNull(ApiAuditLoggingFilter.extractResourceId("/api/system/audit-logs", "audit_log"));
        assertEquals("admin", ApiAuditLoggingFilter.extractResourceId("/api/system/accounts/admin", "account"));
    }

    @Test void operationDescribesWhatWasDone() {
        assertEquals("LIST", ApiAuditLoggingFilter.resolveOperation("GET", "/api/leaders", "leader", null));
        assertEquals("VIEW", ApiAuditLoggingFilter.resolveOperation("GET", "/api/leaders/" + LEADER, "leader", LEADER));
        assertEquals("VIEW", ApiAuditLoggingFilter.resolveOperation("GET", "/api/auth/me", "auth", null));
        assertEquals("CREATE", ApiAuditLoggingFilter.resolveOperation("POST", "/api/leaders", "leader", null));
        assertEquals("UPDATE", ApiAuditLoggingFilter.resolveOperation("PUT", "/api/leaders/" + LEADER, "leader", LEADER));
        assertEquals("DELETE", ApiAuditLoggingFilter.resolveOperation("DELETE", "/api/leaders/" + LEADER, "leader", LEADER));
        assertEquals("TOGGLE_STATUS", ApiAuditLoggingFilter.resolveOperation("PATCH", "/api/parishes/" + LEADER + "/toggle-status", "parish", LEADER));
        assertEquals("LOGIN", ApiAuditLoggingFilter.resolveOperation("POST", "/api/auth/login", "auth", null));
    }

    @Test void sensitiveQueryParametersAreNeverStored() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/leaders");
        request.setQueryString("status=true&token=abc&size=20&Password=x&otp=123456");
        assertEquals("status=true&size=20", ApiAuditLoggingFilter.sanitizeQuery(request));
        assertNull(ApiAuditLoggingFilter.sanitizeQuery(new MockHttpServletRequest("GET", "/api/leaders")));
    }
}
