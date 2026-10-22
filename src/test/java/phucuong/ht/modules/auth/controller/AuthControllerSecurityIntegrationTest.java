package phucuong.ht.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.LoginRequest;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.model.response.AuthenticatedUserResponse;
import phucuong.ht.modules.auth.service.AuthService;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class AuthControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowPublicLoginEndpoint() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("superadminpc");
        request.setPassword("htgppc2026");

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(sampleTokenResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(jsonPath("$.data.user.username").value("superadminpc"));
    }

    @Test
    void shouldRejectMeEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void shouldRejectAdminSummaryWithoutRequiredPermission() throws Exception {
        String token = jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "user-a",
                null,
                List.of("SUPER_ADMIN"),
                List.of()
        ));

        mockMvc.perform(get("/api/auth/admin/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAdminSummaryWithRequiredPermission() throws Exception {
        String token = jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all")
        ));

        when(authService.adminSummary(any())).thenReturn(AuthenticatedUserResponse.builder()
                .username("superadminpc")
                .leaderId(null)
                .roles(List.of("SUPER_ADMIN"))
                .permissions(List.of("auth.session.manage.all"))
                .build());

        mockMvc.perform(get("/api/auth/admin/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("auth.session.manage.all"));
    }

    @Test
    void shouldRequireTemporaryPasswordChangeBeforeBusinessApiUse() throws Exception {
        String token = jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "new-admin",
                null,
                List.of("ADMIN_PARISH"),
                List.of("auth.session.manage.all"),
                true
        ));

        mockMvc.perform(get("/api/auth/admin/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowRefreshTokenFromCookie() throws Exception {
        when(authService.refresh(any())).thenReturn(sampleTokenResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token-cookie")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).refresh(eq("refresh-token-cookie"));
    }

    @Test
    void shouldRejectRefreshWithoutCookie() throws Exception {
        when(authService.refresh(isNull())).thenThrow(new ApiException(UNAUTHORIZED, phucuong.ht.common.exception.errorCode.INVALID_TOKEN, "Refresh token la bat buoc"));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void shouldAllowPublicLogoutAndClearOnlyCurrentSessionCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token-cookie"))
                        .header("Authorization", "Bearer access-token-header"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().maxAge("accessToken", 0));

        verify(authService).logout(eq("refresh-token-cookie"), eq("access-token-header"));
    }

    @Test
    void shouldRequireAuthenticationForSwaggerUiEvenWhenCookieExists() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")
                        .cookie(new jakarta.servlet.http.Cookie("accessToken", "invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAuthenticationForSwaggerInitializer() throws Exception {
        mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private AuthTokenResponse sampleTokenResponse() {
        return AuthTokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .accessTokenExpiresInMs(900000L)
                .refreshTokenExpiresInMs(604800000L)
                .user(AuthenticatedUserResponse.builder()
                        .username("superadminpc")
                        .leaderId(null)
                        .roles(List.of("SUPER_ADMIN"))
                        .permissions(List.of("auth.session.manage.all"))
                        .build())
                .build();
    }
}
