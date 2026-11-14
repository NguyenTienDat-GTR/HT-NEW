package phucuong.ht.modules.mobile.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.modules.auth.model.request.LoginRequest;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.model.response.AuthenticatedUserResponse;
import phucuong.ht.modules.auth.service.AuthService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng nhập / làm mới / đăng xuất của app di động: token trong thân JSON, không đọc hay ghi cookie, không lưu đệm.
 * Đồng thời khẳng định {@code /api/auth/*} của web vẫn chỉ phát refresh token qua cookie.
 */
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class MobileAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginReturnsBothTokensInBodyAndSetsNoCookie() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString(), any())).thenReturn(tokens());

        mockMvc.perform(post("/api/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ht.demo",
                                "password", "mat-khau-thu",
                                "rememberMe", true,
                                "deviceId", "device-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.username").value("ht.demo"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void loginRejectsMissingCredentialsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(LoginRequest.class), anyString(), any());
    }

    @Test
    void refreshUsesOnlyTheBodyTokenAndIgnoresCookies() throws Exception {
        when(authService.refresh("body-refresh")).thenReturn(tokens());

        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookie-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verify(authService).refresh("body-refresh");
        verify(authService, never()).refresh("cookie-refresh");
    }

    @Test
    void refreshWithOnlyACookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookie-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(anyString());
    }

    @Test
    void logoutTakesTokensFromBodyAndClearsNoCookie() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r1\",\"accessToken\":\"a1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).logout(eq("r1"), eq("a1"));
    }

    @Test
    void logoutToleratesAnEmptyBody() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(authService).logout(isNull(), isNull());
    }

    @Test
    void webLoginStillSendsRefreshTokenOnlyInCookie() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString(), any())).thenReturn(tokens());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "ht.demo", "password", "mat-khau-thu"))))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();

        assertNull(objectMapper.readTree(body).path("data").get("refreshToken"));
    }

    private AuthTokenResponse tokens() {
        return AuthTokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .accessTokenExpiresInMs(900_000L)
                .refreshTokenExpiresInMs(2_592_000_000L)
                .rememberMe(true)
                .user(AuthenticatedUserResponse.builder()
                        .username("ht.demo")
                        .roles(List.of("ADMIN_DEANERY"))
                        .permissions(List.of())
                        .build())
                .build();
    }
}
