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

    @Test
    void shouldRejectTokenWithValidSignatureButWrongIssuerOrAudience() {
        AuthProperties wrongIssuerProperties = properties("other-issuer", "ht-management-client");
        jwtUtils wrongIssuerSigner = new jwtUtils(wrongIssuerProperties);
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadminpc", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all"));

        String wrongIssuerToken = wrongIssuerSigner.generateAccessToken(principal, "session-123", "access-123");
        assertThrows(RuntimeException.class, () -> jwtUtils.parseAccessTokenClaims(wrongIssuerToken));

        AuthProperties wrongAudienceProperties = properties("ht-management", "other-audience");
        jwtUtils wrongAudienceSigner = new jwtUtils(wrongAudienceProperties);
        String wrongAudienceToken = wrongAudienceSigner.generateAccessToken(principal, "session-123", "access-123");
        assertThrows(RuntimeException.class, () -> jwtUtils.parseAccessTokenClaims(wrongAudienceToken));
    }

    private AuthProperties properties(String issuer, String audience) {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setAccessSecret("ht-management-access-secret-key-for-jwt-signing-2026");
        properties.getJwt().setRefreshSecret("ht-management-refresh-secret-key-for-jwt-signing-2026");
        properties.getJwt().setIssuer(issuer);
        properties.getJwt().setAudience(audience);
        properties.getJwt().setAccessTokenExpirationMs(90_000L);
        properties.getJwt().setRefreshTokenExpirationMs(604_800_000L);
        return properties;
    }

    @Test
    void accessTokenWithManyPermissionsShouldFitInsideABrowserCookieAndRoundTrip() {
        List<String> permissions = List.of("organization.leader.toggle.parish","training.participation.submit.parish","organization.position.create.parish","organization.position.read.parish","organization.position.update.parish","organization.executive_board_assignment.read.parish","system.audit_log.read.parish","organization.executive_board_assignment.create.parish","system.account.reset_password.parish","analytics.dashboard.read.parish","system.account_permission.manage.parish","system.account.manage.parish","system.account_role.assign.parish","organization.leader.create.parish","organization.leader.read.parish","organization.parish.update.parish","organization.parish.read.parish","organization.leader.update.parish","organization.executive_board_assignment.toggle.parish","system.role.toggle.parish","system.role.update.parish","system.role.read.parish","system.role.create.parish","organization.leader_certificate.read.parish","system.role_permission.revoke.parish","system.role_permission.assign.parish","system.role_permission.read.parish","training.participation.read.parish");
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "datgiaoxubencat510", java.util.UUID.randomUUID().toString(), List.of("ADMIN_PARISH"), permissions);

        String token = jwtUtils.generateAccessToken(principal, java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString());

        assertTrue(token.length() < 1800, "Token dài " + token.length());
        assertEquals(permissions, jwtUtils.parseAccessTokenClaims(token).principal().getPermissions());
    }

    @Test
    void accessTokenWithoutPermissionsShouldRoundTripAsEmptyList() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("locked", null, List.of(), List.of());
        String token = jwtUtils.generateAccessToken(principal, "s", "t");
        assertEquals(List.of(), jwtUtils.parseAccessTokenClaims(token).principal().getPermissions());
    }
}
