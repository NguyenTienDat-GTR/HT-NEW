package phucuong.ht.modules.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.model.request.RoleCreateRequest;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.service.RoleManagementService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoleControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private RoleManagementService roleManagementService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowCreateRoleWithAuthority() throws Exception {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("CUSTOM_ROLE");
        request.setRoleName("Custom Role");

        when(roleManagementService.create(any(RoleCreateRequest.class), any())).thenReturn(RoleResponse.builder()
                .roleCode("CUSTOM_ROLE")
                .roleName("Custom Role")
                .status(true)
                .build());

        mockMvc.perform(post("/api/system/roles")
                        .header("Authorization", bearer(token(List.of("SUPER_ADMIN"), List.of("system.role.create.all"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("CUSTOM_ROLE"));
    }

    @Test
    void shouldRejectCreateRoleWithoutAuthority() throws Exception {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("CUSTOM_ROLE");
        request.setRoleName("Custom Role");

        mockMvc.perform(post("/api/system/roles")
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
