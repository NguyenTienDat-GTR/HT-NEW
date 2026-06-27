package phucuong.ht.modules.diocese.controller;

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
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.security.DioceseAccessEvaluator;
import phucuong.ht.modules.diocese.service.DioceseService;

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

    @Test
    void shouldRejectDioceseListWithoutToken() throws Exception {
        mockMvc.perform(get("/api/dioceses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void shouldAllowSuperAdminToListDioceses() throws Exception {
        when(dioceseAccessEvaluator.canList(any())).thenReturn(true);
        when(dioceseService.findAll(any())).thenReturn(new PageImpl<>(List.of(sampleDiocese("DIO1"))));

        mockMvc.perform(get("/api/dioceses")
                        .header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].dioceseCode").value("DIO1"));
    }

    @Test
    void shouldDenyScopedAdminListEndpoint() throws Exception {
        when(dioceseAccessEvaluator.canList(any())).thenReturn(false);

        mockMvc.perform(get("/api/dioceses")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.READ_DIOCESE)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowScopedAdminToReadOwnDiocese() throws Exception {
        when(dioceseAccessEvaluator.canRead(any(), eq("DIO1"))).thenReturn(true);
        when(dioceseService.findById("DIO1")).thenReturn(sampleDiocese("DIO1"));

        mockMvc.perform(get("/api/dioceses/DIO1")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.READ_DIOCESE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dioceseCode").value("DIO1"));
    }

    @Test
    void shouldDenyScopedAdminReadingAnotherDiocese() throws Exception {
        when(dioceseAccessEvaluator.canRead(any(), eq("DIO2"))).thenReturn(false);

        mockMvc.perform(get("/api/dioceses/DIO2")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.READ_DIOCESE)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowScopedAdminToUpdateOwnDiocese() throws Exception {
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        request.setName("Updated Diocese");
        when(dioceseAccessEvaluator.canUpdate(any(), eq("DIO1"))).thenReturn(true);
        when(dioceseService.update(eq("DIO1"), any(DioceseUpdateRequest.class), any())).thenReturn(sampleDiocese("DIO1"));

        mockMvc.perform(put("/api/dioceses/DIO1")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.UPDATE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dioceseCode").value("DIO1"));
    }

    @Test
    void shouldDenyScopedAdminWithoutResolvableDioceseOnUpdate() throws Exception {
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        request.setName("Updated Diocese");
        when(dioceseAccessEvaluator.canUpdate(any(), eq("DIO1"))).thenReturn(false);

        mockMvc.perform(put("/api/dioceses/DIO1")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.UPDATE_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldDenyScopedAdminCreateEndpoint() throws Exception {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("New Diocese");
        request.setUnionName("Union");
        when(dioceseAccessEvaluator.canCreate(any())).thenReturn(false);

        mockMvc.perform(post("/api/dioceses")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.READ_DIOCESE))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowSuperAdminCreateAndDelete() throws Exception {
        DioceseCreateRequest createRequest = new DioceseCreateRequest();
        createRequest.setName("New Diocese");
        createRequest.setUnionName("Union");
        when(dioceseAccessEvaluator.canCreate(any())).thenReturn(true);
        when(dioceseAccessEvaluator.canDelete(any())).thenReturn(true);
        when(dioceseService.create(any(DioceseCreateRequest.class), any())).thenReturn(sampleDiocese("DIO1"));

        mockMvc.perform(post("/api/dioceses")
                        .header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dioceseCode").value("DIO1"));

        mockMvc.perform(delete("/api/dioceses/DIO1")
                        .header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldDenyScopedAdminDeleteEndpoint() throws Exception {
        when(dioceseAccessEvaluator.canDelete(any())).thenReturn(false);

        mockMvc.perform(delete("/api/dioceses/DIO1")
                        .header("Authorization", bearer(dioceseAdminToken(List.of(DioceseAccessEvaluator.UPDATE_DIOCESE)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    private Diocese sampleDiocese(String dioceseCode) {
        Diocese diocese = new Diocese();
        diocese.setDioceseCode(dioceseCode);
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
                        DioceseAccessEvaluator.DELETE_ALL
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
