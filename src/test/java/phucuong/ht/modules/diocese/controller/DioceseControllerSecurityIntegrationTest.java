package phucuong.ht.modules.diocese.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.security.DioceseAccessEvaluator;
import phucuong.ht.modules.diocese.service.DioceseService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DioceseControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private DioceseService dioceseService;

    @MockitoBean
    private DioceseAccessEvaluator dioceseAccessEvaluator;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowScopedAdminToReadOwnDiocese() throws Exception {
        UUID dioceseId = UUID.randomUUID();
        when(dioceseAccessEvaluator.canRead(any(), eq(dioceseId))).thenReturn(true);
        when(dioceseService.findById(eq(dioceseId), any())).thenReturn(sampleDiocese(dioceseId));

        mockMvc.perform(get("/api/dioceses/" + dioceseId)
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.READ_DIOCESE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dioceseId.toString()));
    }

    @Test
    void shouldAllowScopedAdminToUpdateOwnDiocese() throws Exception {
        UUID dioceseId = UUID.randomUUID();
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        request.setName("Updated Diocese");
        when(dioceseAccessEvaluator.canUpdate(any(), eq(dioceseId))).thenReturn(true);
        when(dioceseService.update(eq(dioceseId), any(DioceseUpdateRequest.class), any())).thenReturn(sampleDiocese(dioceseId));

        mockMvc.perform(put("/api/dioceses/" + dioceseId)
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.UPDATE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dioceseId.toString()));
    }

    @Test
    void shouldAllowSuperAdminCreateAndToggle() throws Exception {
        UUID dioceseId = UUID.randomUUID();
        DioceseCreateRequest createRequest = new DioceseCreateRequest();
        createRequest.setName("New Diocese");
        createRequest.setUnionName("Union");
        StatusToggleRequest toggleRequest = new StatusToggleRequest();
        toggleRequest.setConfirmationText("Xác nhận");
        when(dioceseAccessEvaluator.canCreate(any())).thenReturn(true);
        when(dioceseAccessEvaluator.canToggle(any(), eq(dioceseId))).thenReturn(true);
        when(dioceseService.create(any(DioceseCreateRequest.class), any())).thenReturn(sampleDiocese(dioceseId));
        when(dioceseService.toggleStatus(eq(dioceseId), any(StatusToggleRequest.class), any())).thenReturn(sampleDiocese(dioceseId));

        mockMvc.perform(post("/api/dioceses")
                        .header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dioceseId.toString()));

        mockMvc.perform(patch("/api/dioceses/" + dioceseId + "/toggle-status")
                        .header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toggleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dioceseId.toString()));
    }

    @Test
    void shouldRejectInvalidVietnamesePhoneNumber() throws Exception {
        DioceseCreateRequest createRequest = new DioceseCreateRequest();
        createRequest.setName("New Diocese");
        createRequest.setUnionName("Union");
        createRequest.setPhoneNumber("09123456789");
        when(dioceseAccessEvaluator.canCreate(any())).thenReturn(true);

        mockMvc.perform(post("/api/dioceses")
                        .header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAllowSuperAdminToToggleDioceseStatusWithConfirmation() throws Exception {
        UUID dioceseId = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        when(dioceseAccessEvaluator.canToggle(any(), eq(dioceseId))).thenReturn(true);
        when(dioceseService.toggleStatus(eq(dioceseId), any(StatusToggleRequest.class), any())).thenReturn(sampleDiocese(dioceseId));

        mockMvc.perform(patch("/api/dioceses/" + dioceseId + "/toggle-status")
                        .header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dioceseId.toString()));
    }

    @Test
    void shouldRejectToggleWithoutConfirmation() throws Exception {
        UUID dioceseId = UUID.randomUUID();
        when(dioceseAccessEvaluator.canToggle(any(), eq(dioceseId))).thenReturn(true);

        mockMvc.perform(patch("/api/dioceses/" + dioceseId + "/toggle-status")
                        .header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Diocese sampleDiocese(UUID id) {
        Diocese diocese = new Diocese();
        diocese.setId(id);
        diocese.setName("Sample Diocese");
        diocese.setUnionName("Sample Union");
        return diocese;
    }

    private String superAdminToken() {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of(
                        DioceseAccessEvaluator.CREATE_ALL,
                        DioceseAccessEvaluator.READ_ALL,
                        DioceseAccessEvaluator.UPDATE_ALL,
                        DioceseAccessEvaluator.TOGGLE_ALL
                )
        ));
    }

    private String dioceseAdminToken(List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                permissions
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
