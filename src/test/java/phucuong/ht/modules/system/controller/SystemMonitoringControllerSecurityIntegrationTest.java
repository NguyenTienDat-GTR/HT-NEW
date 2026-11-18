package phucuong.ht.modules.system.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Summary;
import phucuong.ht.modules.system.service.SystemMonitoringService;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemMonitoringControllerSecurityIntegrationTest {

    private static final String PATH = "/api/system/monitoring/overview";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private SystemMonitoringService systemMonitoringService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void systemWideLogReaderCanOpenMonitoring() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        when(systemMonitoringService.overview(24)).thenReturn(new SystemMonitoringResponse(24, 60, now.minusHours(24), now,
                new Summary(12, 0, 0, 0, 1, 1, 0, 50), null, 0L, true,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get(PATH).header("Authorization", bearer("system.audit_log.read.all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalRequests").value(12));
    }

    @Test
    void unitLevelLogReaderIsRejected() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer("system.audit_log.read.diocese")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidWindowIsReportedAsBadRequest() throws Exception {
        when(systemMonitoringService.overview(5)).thenThrow(
                new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Khoảng thời gian giám sát không hợp lệ"));

        mockMvc.perform(get(PATH).param("hours", "5").header("Authorization", bearer("system.audit_log.read.all")))
                .andExpect(status().isBadRequest());
    }

    private String bearer(String permission) {
        return "Bearer " + jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal("user", null, List.of("SUPER_ADMIN"), List.of(permission)));
    }
}
