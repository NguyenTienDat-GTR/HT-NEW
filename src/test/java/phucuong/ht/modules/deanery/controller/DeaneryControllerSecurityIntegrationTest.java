package phucuong.ht.modules.deanery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;
import phucuong.ht.modules.deanery.service.DeaneryService;

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
class DeaneryControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private DeaneryService deaneryService;

    @MockitoBean
    private DeaneryAccessEvaluator deaneryAccessEvaluator;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowSuperAdminToListDeaneries() throws Exception {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        when(deaneryAccessEvaluator.canList(any())).thenReturn(true);
        when(deaneryService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(sampleDeanery(deaneryId, dioceseId))));

        mockMvc.perform(get("/api/deaneries")
                        .header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(deaneryId.toString()))
                .andExpect(jsonPath("$.data.content[0].dioceseId").value(dioceseId.toString()));
    }

    @Test
    void shouldAllowScopedAdminToReadOwnDeanery() throws Exception {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        when(deaneryAccessEvaluator.canRead(any(), eq(deaneryId))).thenReturn(true);
        when(deaneryService.findById(eq(deaneryId), any())).thenReturn(sampleDeanery(deaneryId, dioceseId));

        mockMvc.perform(get("/api/deaneries/" + deaneryId)
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.READ_DEANERY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deaneryId.toString()))
                .andExpect(jsonPath("$.data.dioceseId").value(dioceseId.toString()));
    }

    @Test
    void shouldAllowDeaneryAdminToUpdateOwnDeanery() throws Exception {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        DeaneryUpdateRequest request = new DeaneryUpdateRequest();
        request.setName("Updated Deanery");
        when(deaneryAccessEvaluator.canUpdate(any(), eq(deaneryId))).thenReturn(true);
        when(deaneryService.update(eq(deaneryId), any(DeaneryUpdateRequest.class), any())).thenReturn(sampleDeanery(deaneryId, dioceseId));

        mockMvc.perform(put("/api/deaneries/" + deaneryId)
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.UPDATE_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deaneryId.toString()));
    }

    @Test
    void shouldAllowDioceseAdminCreateAndToggleWithinScope() throws Exception {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        DeaneryCreateRequest createRequest = new DeaneryCreateRequest();
        createRequest.setName("New Deanery");
        createRequest.setAssociationName("Association");
        createRequest.setDioceseId(dioceseId);
        StatusToggleRequest toggleRequest = new StatusToggleRequest();
        toggleRequest.setConfirmationText("Xác nhận");
        when(deaneryAccessEvaluator.canCreate(any())).thenReturn(true);
        when(deaneryAccessEvaluator.canToggle(any(), eq(deaneryId))).thenReturn(true);
        when(deaneryService.create(any(DeaneryCreateRequest.class), any())).thenReturn(sampleDeanery(deaneryId, dioceseId));
        when(deaneryService.toggleStatus(eq(deaneryId), any(StatusToggleRequest.class), any())).thenReturn(sampleDeanery(deaneryId, dioceseId));

        mockMvc.perform(post("/api/deaneries")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DeaneryAccessEvaluator.CREATE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deaneryId.toString()))
                .andExpect(jsonPath("$.data.dioceseId").value(dioceseId.toString()));

        mockMvc.perform(patch("/api/deaneries/" + deaneryId + "/toggle-status")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DeaneryAccessEvaluator.TOGGLE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toggleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deaneryId.toString()));
    }

    @Test
    void shouldAllowDioceseAdminToToggleDeaneryStatus() throws Exception {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        when(deaneryAccessEvaluator.canToggle(any(), eq(deaneryId))).thenReturn(true);
        when(deaneryService.toggleStatus(eq(deaneryId), any(StatusToggleRequest.class), any())).thenReturn(sampleDeanery(deaneryId, dioceseId));

        mockMvc.perform(patch("/api/deaneries/" + deaneryId + "/toggle-status")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DeaneryAccessEvaluator.UPDATE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deaneryId.toString()));
    }

    private DeaneryResponse sampleDeanery(UUID deaneryId, UUID dioceseId) {
        return DeaneryResponse.builder()
                .id(deaneryId)
                .dioceseId(dioceseId)
                .name("Sample Deanery")
                .associationName("Sample Association")
                .status(true)
                .build();
    }

    private String superAdminToken() {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of(
                        DeaneryAccessEvaluator.CREATE_ALL,
                        DeaneryAccessEvaluator.READ_ALL,
                        DeaneryAccessEvaluator.UPDATE_ALL,
                        DeaneryAccessEvaluator.TOGGLE_ALL
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

    private String deaneryAdminToken(List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                permissions
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
