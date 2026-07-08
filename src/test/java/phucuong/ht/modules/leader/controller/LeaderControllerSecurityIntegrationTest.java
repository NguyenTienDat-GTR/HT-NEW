package phucuong.ht.modules.leader.controller;

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
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.leader.service.LeaderService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
class LeaderControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private LeaderService leaderService;

    @MockitoBean
    private LeaderAccessEvaluator leaderAccessEvaluator;

    @Test
    void shouldAllowParishAdminToListLeaders() throws Exception {
        UUID leaderId = UUID.randomUUID();
        when(leaderAccessEvaluator.canList(any())).thenReturn(true);
        when(leaderService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(sampleLeader(leaderId))));

        mockMvc.perform(get("/api/leaders")
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.READ_PARISH)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(leaderId.toString()));
    }

    @Test
    void shouldAllowParishAdminToCreateReadAndUpdateLeaderInScope() throws Exception {
        UUID leaderId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        LeaderCreateRequest createRequest = new LeaderCreateRequest();
        createRequest.setHolyName("Phaolo");
        createRequest.setFullName("Nguyen An");
        createRequest.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        createRequest.setEmail("leader@example.com");
        createRequest.setGender(LeaderGender.NAM);
        createRequest.setLeaderLevel(LeaderLevel.HT_XU);
        createRequest.setParishId(parishId);

        LeaderUpdateRequest updateRequest = new LeaderUpdateRequest();
        updateRequest.setFullName("Nguyen Binh");
        updateRequest.setGender(LeaderGender.NU);

        when(leaderAccessEvaluator.canCreate(any())).thenReturn(true);
        when(leaderAccessEvaluator.canRead(any(), eq(leaderId))).thenReturn(true);
        when(leaderAccessEvaluator.canUpdate(any(), eq(leaderId))).thenReturn(true);
        when(leaderService.create(any(LeaderCreateRequest.class), any())).thenReturn(sampleLeader(leaderId));
        when(leaderService.findById(eq(leaderId), any())).thenReturn(sampleLeader(leaderId));
        when(leaderService.update(eq(leaderId), any(LeaderUpdateRequest.class), any())).thenReturn(sampleLeader(leaderId));

        mockMvc.perform(post("/api/leaders")
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.CREATE_PARISH))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(leaderId.toString()));

        mockMvc.perform(get("/api/leaders/" + leaderId)
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.READ_PARISH)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("NAM"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen An"));

        mockMvc.perform(put("/api/leaders/" + leaderId)
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.UPDATE_PARISH))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaderLevel").value("HT_XU"));
    }

    @Test
    void shouldReturnForbiddenWhenScopeEvaluatorRejects() throws Exception {
        UUID leaderId = UUID.randomUUID();
        when(leaderAccessEvaluator.canRead(any(), eq(leaderId))).thenReturn(false);

        mockMvc.perform(get("/api/leaders/" + leaderId)
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.READ_PARISH)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowParishAdminToToggleLeaderStatus() throws Exception {
        UUID leaderId = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        when(leaderAccessEvaluator.canToggle(any(), eq(leaderId))).thenReturn(true);
        when(leaderService.toggleStatus(eq(leaderId), any(StatusToggleRequest.class), any())).thenReturn(sampleLeader(leaderId));

        mockMvc.perform(patch("/api/leaders/" + leaderId + "/toggle-status")
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.UPDATE_PARISH))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(leaderId.toString()));
    }

    @Test
    void shouldRejectInvalidVietnamesePhoneNumber() throws Exception {
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setHolyName("Phaolo");
        request.setFullName("Nguyen An");
        request.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        request.setEmail("leader@example.com");
        request.setGender(LeaderGender.NAM);
        request.setLeaderLevel(LeaderLevel.NONE);
        request.setParishId(UUID.randomUUID());
        request.setPhoneNumber("09123456789");

        when(leaderAccessEvaluator.canCreate(any())).thenReturn(true);

        mockMvc.perform(post("/api/leaders")
                        .header("Authorization", bearer(parishAdminToken(List.of(LeaderAccessEvaluator.CREATE_PARISH))))
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private LeaderResponse sampleLeader(UUID id) {
        return LeaderResponse.builder()
                .id(id)
                .holyName("Phaolo")
                .fullName("Nguyen An")
                .firstName("An")
                .lastName("Nguyen")
                .gender(LeaderGender.NAM)
                .leaderLevel(LeaderLevel.HT_XU)
                .status(true)
                .build();
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
