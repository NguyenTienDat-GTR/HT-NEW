package phucuong.ht.modules.deanery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;
import phucuong.ht.modules.deanery.service.DeaneryService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void shouldRejectDeaneryListWithoutToken() throws Exception {
        mockMvc.perform(get("/api/deaneries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void shouldAllowSuperAdminToListDeaneries() throws Exception {
        when(deaneryAccessEvaluator.canList(any())).thenReturn(true);
        when(deaneryService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(sampleDeanery("DEA1"))));

        mockMvc.perform(get("/api/deaneries")
                        .header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].deaneryCode").value("DEA1"))
                .andExpect(jsonPath("$.data.content[0].dioceseCode").value("DIO1"))
                .andExpect(jsonPath("$.data.content[0].diocese").doesNotExist());
    }

    @Test
    void shouldAllowDioceseAdminToListScopedDeaneries() throws Exception {
        when(deaneryAccessEvaluator.canList(any())).thenReturn(true);
        when(deaneryService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(sampleDeanery("DEA1"))));

        mockMvc.perform(get("/api/deaneries")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DeaneryAccessEvaluator.READ_DIOCESE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].deaneryCode").value("DEA1"))
                .andExpect(jsonPath("$.data.content[0].dioceseCode").value("DIO1"))
                .andExpect(jsonPath("$.data.content[0].diocese").doesNotExist());
    }

    @Test
    void shouldAllowDeaneryAdminToListOwnDeanery() throws Exception {
        when(deaneryAccessEvaluator.canList(any())).thenReturn(true);
        when(deaneryService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(sampleDeanery("DEA1"))));

        mockMvc.perform(get("/api/deaneries")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.READ_DEANERY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].deaneryCode").value("DEA1"))
                .andExpect(jsonPath("$.data.content[0].dioceseCode").value("DIO1"))
                .andExpect(jsonPath("$.data.content[0].diocese").doesNotExist());
    }

    @Test
    void shouldAllowScopedAdminToReadOwnDeanery() throws Exception {
        when(deaneryAccessEvaluator.canRead(any(), eq("DEA1"))).thenReturn(true);
        when(deaneryService.findById("DEA1")).thenReturn(sampleDeanery("DEA1"));

        mockMvc.perform(get("/api/deaneries/DEA1")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.READ_DEANERY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deaneryCode").value("DEA1"))
                .andExpect(jsonPath("$.data.dioceseCode").value("DIO1"))
                .andExpect(jsonPath("$.data.diocese").doesNotExist());
    }

    @Test
    void shouldDenyScopedAdminReadingAnotherDeanery() throws Exception {
        when(deaneryAccessEvaluator.canRead(any(), eq("DEA2"))).thenReturn(false);

        mockMvc.perform(get("/api/deaneries/DEA2")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.READ_DEANERY)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowDeaneryAdminToUpdateOwnDeanery() throws Exception {
        DeaneryUpdateRequest request = new DeaneryUpdateRequest();
        request.setName("Updated Deanery");
        when(deaneryAccessEvaluator.canUpdate(any(), eq("DEA1"))).thenReturn(true);
        when(deaneryService.update(eq("DEA1"), any(DeaneryUpdateRequest.class), any())).thenReturn(sampleDeanery("DEA1"));

        mockMvc.perform(put("/api/deaneries/DEA1")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.UPDATE_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deaneryCode").value("DEA1"))
                .andExpect(jsonPath("$.data.dioceseCode").value("DIO1"))
                .andExpect(jsonPath("$.data.diocese").doesNotExist());
    }

    @Test
    void shouldDenyDeaneryAdminCreateEndpoint() throws Exception {
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setName("New Deanery");
        request.setAssociationName("Association");
        request.setDioceseCode("DIO1");
        when(deaneryAccessEvaluator.canCreate(any())).thenReturn(false);

        mockMvc.perform(post("/api/deaneries")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.READ_DEANERY))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowDioceseAdminCreateAndDeleteWithinScope() throws Exception {
        DeaneryCreateRequest createRequest = new DeaneryCreateRequest();
        createRequest.setName("New Deanery");
        createRequest.setAssociationName("Association");
        createRequest.setDioceseCode("DIO1");
        when(deaneryAccessEvaluator.canCreate(any())).thenReturn(true);
        when(deaneryAccessEvaluator.canDelete(any())).thenReturn(true);
        when(deaneryService.create(any(DeaneryCreateRequest.class), any())).thenReturn(sampleDeanery("DEA1"));

        mockMvc.perform(post("/api/deaneries")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DeaneryAccessEvaluator.CREATE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deaneryCode").value("DEA1"))
                .andExpect(jsonPath("$.data.dioceseCode").value("DIO1"))
                .andExpect(jsonPath("$.data.diocese").doesNotExist());

        mockMvc.perform(delete("/api/deaneries/DEA1")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DeaneryAccessEvaluator.DELETE_DIOCESE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldDenyDeaneryAdminDeleteEndpoint() throws Exception {
        when(deaneryAccessEvaluator.canDelete(any())).thenReturn(false);

        mockMvc.perform(delete("/api/deaneries/DEA1")
                        .header("Authorization", bearer(deaneryAdminToken(List.of(DeaneryAccessEvaluator.UPDATE_DEANERY)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    private DeaneryResponse sampleDeanery(String deaneryCode) {
        return DeaneryResponse.builder()
                .deaneryCode(deaneryCode)
                .name("Sample Deanery")
                .associationName("Sample Association")
                .dioceseCode("DIO1")
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
                        DeaneryAccessEvaluator.DELETE_ALL
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
