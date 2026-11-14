package phucuong.ht.modules.mobile.push.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.mobile.push.model.entity.PushDevice;
import phucuong.ht.modules.mobile.push.repository.PushDeviceRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class MobilePushControllerIntegrationTest {

    private static final String USER = "push.test.user";
    private static final String TOKEN = "ExponentPushToken[controller-test-1]";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private jwtUtils jwtUtils;
    @Autowired
    private PushDeviceRepository repository;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        repository.findByUsername(USER).forEach(repository::delete);
        repository.findByPushToken(TOKEN).ifPresent(repository::delete);
    }

    private String bearer() {
        return "Bearer " + jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(USER, null, List.of("ADMIN_PARISH"), List.of()));
    }

    private String body(String deviceId, String token, String platform) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "deviceId", deviceId, "pushToken", token, "platform", platform, "appVersion", "1.0.0"));
    }

    @Test
    void registrationRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/mobile/push/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dev1", TOKEN, "android")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registersTheDeviceForTheCurrentAccountAndRefreshesItOnRepeat() throws Exception {
        mockMvc.perform(post("/api/mobile/push/devices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dev1", TOKEN, "ANDROID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post("/api/mobile/push/devices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dev1", TOKEN, "android")))
                .andExpect(status().isOk());

        List<PushDevice> devices = repository.findByUsername(USER);
        assertEquals(1, devices.size());
        assertEquals(TOKEN, devices.get(0).getPushToken());
        assertEquals("android", devices.get(0).getPlatform());
    }

    @Test
    void rejectsInvalidTokenAndPlatform() throws Exception {
        mockMvc.perform(post("/api/mobile/push/devices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dev1", "khong-phai-ma-expo", "android")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mobile/push/devices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dev1", TOKEN, "windows")))
                .andExpect(status().isBadRequest());
        assertTrue(repository.findByUsername(USER).isEmpty());
    }

    @Test
    void unregisterRemovesOnlyTheCallersDevice() throws Exception {
        mockMvc.perform(post("/api/mobile/push/devices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dev1", TOKEN, "ios")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/mobile/push/devices/dev1").header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/mobile/push/devices/dev1").header("Authorization", bearer()))
                .andExpect(status().isOk());

        assertTrue(repository.findByUsername(USER).isEmpty());
    }
}
