package phucuong.ht.modules.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.system.model.request.PermissionCreateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.service.PermissionManagementService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PermissionControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private PermissionManagementService permissionManagementService;

    @Test
    void shouldAllowCreatePermissionWithAuthority() throws Exception {
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setPermissionCode("custom.permission.read.diocese");
        request.setPermissionName("Custom permission");
        request.setModuleCode("system");
        request.setResourceCode("permission");
        request.setActionCode("read");
        request.setScopeCode("diocese");

        when(permissionManagementService.create(any(PermissionCreateRequest.class), any())).thenReturn(PermissionResponse.builder()
                .permissionCode("custom.permission.read.diocese")
                .permissionName("Custom permission")
                .status(true)
                .build());

        mockMvc.perform(post("/api/system/permissions")
                        .header("Authorization", bearer(token(List.of("SUPER_ADMIN"), List.of("system.permission.create.all"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCode").value("custom.permission.read.diocese"));
    }

    @Test
    void shouldRejectCreatePermissionWithoutAuthority() throws Exception {
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setPermissionCode("custom.permission.read.diocese");
        request.setPermissionName("Custom permission");

        mockMvc.perform(post("/api/system/permissions")
                        .header("Authorization", bearer(token(List.of("SUPER_ADMIN"), List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private String token(List<String> roles, List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal("user", null, roles, permissions));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
