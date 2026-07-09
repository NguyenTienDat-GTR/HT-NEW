package phucuong.ht.modules.executiveBoard.controller;

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
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentCreateRequest;
import phucuong.ht.modules.executiveBoard.model.response.ExecutiveBoardAssignmentResponse;
import phucuong.ht.modules.executiveBoard.security.ExecutiveBoardAssignmentAccessEvaluator;
import phucuong.ht.modules.executiveBoard.service.ExecutiveBoardAssignmentService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExecutiveBoardAssignmentControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private ExecutiveBoardAssignmentService assignmentService;

    @MockitoBean
    private ExecutiveBoardAssignmentAccessEvaluator accessEvaluator;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowDeaneryAdminToCreateListAndReadAssignment() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        ExecutiveBoardAssignmentCreateRequest request = new ExecutiveBoardAssignmentCreateRequest();
        request.setUnitType(UnitType.DEANERY);
        request.setUnitId(unitId);
        request.setLeaderId(leaderId);
        request.setPositionCode("POS-1");

        ExecutiveBoardAssignmentResponse response = sampleResponse(assignmentId, unitId, leaderId);

        when(accessEvaluator.canCreate(any(), eq(UnitType.DEANERY))).thenReturn(true);
        when(accessEvaluator.canList(any())).thenReturn(true);
        when(accessEvaluator.canRead(any(), eq(assignmentId))).thenReturn(true);
        when(assignmentService.create(any(ExecutiveBoardAssignmentCreateRequest.class), any())).thenReturn(response);
        when(assignmentService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(response)));
        when(assignmentService.findById(eq(assignmentId), any())).thenReturn(response);

        mockMvc.perform(post("/api/executive-board/assignments")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(
                                ExecutiveBoardAssignmentAccessEvaluator.CREATE_DEANERY,
                                ExecutiveBoardAssignmentAccessEvaluator.READ_DEANERY
                        ))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));

        mockMvc.perform(get("/api/executive-board/assignments")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(ExecutiveBoardAssignmentAccessEvaluator.READ_DEANERY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].positionCode").value("POS-1"));

        mockMvc.perform(get("/api/executive-board/assignments/" + assignmentId)
                        .header("Authorization", bearer(deaneryAdminToken(List.of(ExecutiveBoardAssignmentAccessEvaluator.READ_DEANERY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitType").value("DEANERY"));
    }

    @Test
    void shouldAllowDeaneryAdminToToggleAssignmentStatus() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");

        when(accessEvaluator.canToggle(any(), eq(assignmentId))).thenReturn(true);
        when(assignmentService.toggleStatus(eq(assignmentId), any(StatusToggleRequest.class), any()))
                .thenReturn(sampleResponse(assignmentId, unitId, leaderId));

        mockMvc.perform(patch("/api/executive-board/assignments/" + assignmentId + "/toggle-status")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(ExecutiveBoardAssignmentAccessEvaluator.UPDATE_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
    }

    private ExecutiveBoardAssignmentResponse sampleResponse(UUID assignmentId, UUID unitId, UUID leaderId) {
        return ExecutiveBoardAssignmentResponse.builder()
                .id(assignmentId)
                .unitType(UnitType.DEANERY)
                .unitId(unitId)
                .leaderId(leaderId)
                .positionCode("POS-1")
                .positionName("Hiệp đoàn trưởng")
                .status(true)
                .build();
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
