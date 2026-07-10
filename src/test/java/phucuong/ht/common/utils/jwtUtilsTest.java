package phucuong.ht.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class jwtUtilsTest {

    private jwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setAccessSecret("ht-management-access-secret-key-for-jwt-signing-2026");
        properties.getJwt().setRefreshSecret("ht-management-refresh-secret-key-for-jwt-signing-2026");
        properties.getJwt().setIssuer("ht-management");
        properties.getJwt().setAudience("ht-management-client");
        properties.getJwt().setAccessTokenExpirationMs(90_000L);
        properties.getJwt().setRefreshTokenExpirationMs(604_800_000L);
        jwtUtils = new jwtUtils(properties);
    }

    @Test
    void shouldRoundTripAccessTokenClaims() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadminpc",
                "leader-001",
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all")
        );

        String token = jwtUtils.generateAccessToken(principal, "session-123", "access-123");
        jwtUtils.TokenClaims parsed = jwtUtils.parseAccessTokenClaims(token);

        assertEquals("superadminpc", parsed.principal().getUsername());
        assertEquals("leader-001", parsed.principal().getLeaderId());
        assertEquals(List.of("SUPER_ADMIN"), parsed.principal().getRoles());
        assertEquals(List.of("auth.session.manage.all"), parsed.principal().getPermissions());
        assertEquals("session-123", parsed.sessionId());
        assertEquals("access-123", parsed.tokenId());
        assertNotNull(parsed.expiresAt());
    }

    @Test
    void shouldKeepRefreshTokenClaimsMinimal() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadminpc",
                "leader-001",
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all")
        );

        String token = jwtUtils.generateRefreshToken(principal, "session-123", "refresh-123");
        jwtUtils.TokenClaims parsed = jwtUtils.parseRefreshTokenClaims(token);

        assertEquals("superadminpc", parsed.principal().getUsername());
        assertNull(parsed.principal().getLeaderId());
        assertTrue(parsed.principal().getRoles().isEmpty());
        assertTrue(parsed.principal().getPermissions().isEmpty());
        assertEquals("session-123", parsed.sessionId());
        assertEquals("refresh-123", parsed.tokenId());
    }

    @Test
    void shouldRejectRefreshTokenWhenParsedAsAccessToken() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all")
        );

        String token = jwtUtils.generateRefreshToken(principal, "session-123", "refresh-123");

        assertThrows(RuntimeException.class, () -> jwtUtils.parseAccessToken(token));
    }
}
