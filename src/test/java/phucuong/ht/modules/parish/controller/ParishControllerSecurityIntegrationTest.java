package phucuong.ht.modules.parish.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;
import phucuong.ht.modules.parish.model.response.ParishResponse;
import phucuong.ht.modules.parish.security.ParishAccessEvaluator;
import phucuong.ht.modules.parish.service.ParishService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ParishControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private ParishService parishService;

    @MockitoBean
    private ParishAccessEvaluator parishAccessEvaluator;

    @Test
    void shouldAllowSuperAdminToListParishes() throws Exception {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        when(parishAccessEvaluator.canList(any())).thenReturn(true);
        when(parishService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(sampleParish(parishId, deaneryId))));

        mockMvc.perform(get("/api/parishes")
                        .header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(parishId.toString()))
                .andExpect(jsonPath("$.data.content[0].deaneryId").value(deaneryId.toString()));
    }

    @Test
    void shouldAllowDioceseAdminToReadAndDeleteParishInScope() throws Exception {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        when(parishAccessEvaluator.canRead(any(), eq(parishId))).thenReturn(true);
        when(parishAccessEvaluator.canDelete(any())).thenReturn(true);
        when(parishService.findById(eq(parishId), any())).thenReturn(sampleParish(parishId, deaneryId));

        mockMvc.perform(get("/api/parishes/" + parishId)
                        .header("Authorization", bearer(dioceseAdminToken(List.of(ParishAccessEvaluator.READ_DIOCESE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(parishId.toString()));

        mockMvc.perform(delete("/api/parishes/" + parishId)
                        .header("Authorization", bearer(dioceseAdminToken(List.of(ParishAccessEvaluator.DELETE_DIOCESE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldAllowDeaneryAdminToCreateAndUpdateParishInScope() throws Exception {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        ParishCreateRequest createRequest = new ParishCreateRequest();
        createRequest.setName("New Parish");
        createRequest.setChapterName("Chapter");
        createRequest.setDeaneryId(deaneryId);
        ParishUpdateRequest updateRequest = new ParishUpdateRequest();
        updateRequest.setName("Updated Parish");
        when(parishAccessEvaluator.canCreate(any())).thenReturn(true);
        when(parishAccessEvaluator.canUpdate(any(), eq(parishId))).thenReturn(true);
        when(parishService.create(any(ParishCreateRequest.class), any())).thenReturn(sampleParish(parishId, deaneryId));
        when(parishService.update(eq(parishId), any(ParishUpdateRequest.class), any())).thenReturn(sampleParish(parishId, deaneryId));

        mockMvc.perform(post("/api/parishes")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(ParishAccessEvaluator.CREATE_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(parishId.toString()));

        mockMvc.perform(put("/api/parishes/" + parishId)
                        .header("Authorization", bearer(deaneryAdminToken(List.of(ParishAccessEvaluator.UPDATE_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deaneryId").value(deaneryId.toString()));
    }

    @Test
    void shouldAllowParishAdminToReadAndUpdateOwnParish() throws Exception {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        ParishUpdateRequest request = new ParishUpdateRequest();
        request.setChapterName("Updated Chapter");
        when(parishAccessEvaluator.canRead(any(), eq(parishId))).thenReturn(true);
        when(parishAccessEvaluator.canUpdate(any(), eq(parishId))).thenReturn(true);
        when(parishService.findById(eq(parishId), any())).thenReturn(sampleParish(parishId, deaneryId));
        when(parishService.update(eq(parishId), any(ParishUpdateRequest.class), any())).thenReturn(sampleParish(parishId, deaneryId));

        mockMvc.perform(get("/api/parishes/" + parishId)
                        .header("Authorization", bearer(parishAdminToken(List.of(ParishAccessEvaluator.READ_PARISH)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(parishId.toString()));

        mockMvc.perform(put("/api/parishes/" + parishId)
                        .header("Authorization", bearer(parishAdminToken(List.of(ParishAccessEvaluator.UPDATE_PARISH))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(parishId.toString()));
    }

    @Test
    void shouldReturnForbiddenWhenScopeEvaluatorRejects() throws Exception {
        UUID parishId = UUID.randomUUID();
        when(parishAccessEvaluator.canRead(any(), eq(parishId))).thenReturn(false);

        mockMvc.perform(get("/api/parishes/" + parishId)
                        .header("Authorization", bearer(parishAdminToken(List.of(ParishAccessEvaluator.READ_PARISH)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowDeaneryAdminToToggleParishStatus() throws Exception {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        when(parishAccessEvaluator.canToggle(any(), eq(parishId))).thenReturn(true);
        when(parishService.toggleStatus(eq(parishId), any(StatusToggleRequest.class), any())).thenReturn(sampleParish(parishId, deaneryId));

        mockMvc.perform(patch("/api/parishes/" + parishId + "/toggle-status")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(ParishAccessEvaluator.UPDATE_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(parishId.toString()));
    }

    private ParishResponse sampleParish(UUID parishId, UUID deaneryId) {
        return ParishResponse.builder()
                .id(parishId)
                .deaneryId(deaneryId)
                .name("Sample Parish")
                .chapterName("Sample Chapter")
                .status(true)
                .build();
    }

    private String superAdminToken() {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of(
                        ParishAccessEvaluator.CREATE_ALL,
                        ParishAccessEvaluator.READ_ALL,
                        ParishAccessEvaluator.UPDATE_ALL,
                        ParishAccessEvaluator.DELETE_ALL
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

    private String parishAdminToken(List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                permissions
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
