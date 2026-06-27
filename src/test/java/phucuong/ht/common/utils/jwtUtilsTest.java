package phucuong.ht.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        String token = jwtUtils.generateAccessToken(principal);
        AuthUserDetails parsed = jwtUtils.parseAccessToken(token);

        assertEquals("superadminpc", parsed.getUsername());
        assertEquals("leader-001", parsed.getLeaderId());
        assertEquals(List.of("SUPER_ADMIN"), parsed.getRoles());
        assertEquals(List.of("auth.session.manage.all"), parsed.getPermissions());
    }

    @Test
    void shouldRejectRefreshTokenWhenParsedAsAccessToken() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all")
        );

        String token = jwtUtils.generateRefreshToken(principal);

        assertThrows(RuntimeException.class, () -> jwtUtils.parseAccessToken(token));
    }
}
