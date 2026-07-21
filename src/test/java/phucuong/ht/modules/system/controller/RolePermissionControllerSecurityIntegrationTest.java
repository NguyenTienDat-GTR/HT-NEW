package phucuong.ht.modules.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.model.enums.PermissionEffect;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.service.RolePermissionManagementService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RolePermissionControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private RolePermissionManagementService rolePermissionManagementService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowDioceseAdminAssignRolePermissionWhenItHasAssignAuthority() throws Exception {
        RolePermissionCreateRequest request = new RolePermissionCreateRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCode("organization.position.read.parish");
        request.setEffect(PermissionEffect.ALLOW);

        when(rolePermissionManagementService.create(any(RolePermissionCreateRequest.class), any())).thenReturn(RolePermissionResponse.builder()
                .id("rp-1")
                .roleCode("ADMIN_PARISH")
                .permissionCode("organization.position.read.parish")
                .effect(PermissionEffect.ALLOW)
                .status(true)
                .build());

        mockMvc.perform(post("/api/system/role-permissions")
                .header("Authorization", bearer(token(List.of("ADMIN_DIOCESE"), List.of("system.role_permission.assign.diocese"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN_PARISH"))
                .andExpect(jsonPath("$.data.permissionCode").value("organization.position.read.parish"));
    }

    @Test
    void shouldRejectRolePermissionAssignWithoutAuthority() throws Exception {
        RolePermissionCreateRequest request = new RolePermissionCreateRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCode("organization.position.read.parish");
        request.setEffect(PermissionEffect.ALLOW);

        mockMvc.perform(post("/api/system/role-permissions")
                        .header("Authorization", bearer(token(List.of("ADMIN_DIOCESE"), List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowBulkRolePermissionAssignWithCheckboxPayload() throws Exception {
        RolePermissionBulkRequest request = new RolePermissionBulkRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCodes(List.of("organization.position.read.parish", "organization.position.update.parish"));
        request.setEffect(PermissionEffect.ALLOW);

        when(rolePermissionManagementService.bulkAssign(any(RolePermissionBulkRequest.class), any())).thenReturn(List.of(
                RolePermissionResponse.builder()
                        .id("rp-1")
                        .roleCode("ADMIN_PARISH")
                        .permissionCode("organization.position.read.parish")
                        .effect(PermissionEffect.ALLOW)
                        .status(true)
                        .build(),
                RolePermissionResponse.builder()
                        .id("rp-2")
                        .roleCode("ADMIN_PARISH")
                        .permissionCode("organization.position.update.parish")
                        .effect(PermissionEffect.ALLOW)
                        .status(true)
                        .build()
        ));

        mockMvc.perform(post("/api/system/role-permissions/bulk")
                        .header("Authorization", bearer(token(List.of("ADMIN_DIOCESE"), List.of("system.role_permission.assign.diocese"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].roleCode").value("ADMIN_PARISH"));
    }

    @Test
    void shouldBindRoleAndEffectFiltersFromJsonFilters() throws Exception {
        when(rolePermissionManagementService.findAll(any(BaseSearchRequest.class), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/system/role-permissions")
                        .param("page", "0")
                        .param("size", "10")
                        .param("filters", "{\"role.roleCode\":\"ADMIN_DIOCESE\",\"effect\":\"ALLOW\"}")
                        .header("Authorization", bearer(token(
                                List.of("ADMIN_DIOCESE"),
                                List.of("system.role_permission.read.diocese")
                        ))))
                .andExpect(status().isOk());

        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(rolePermissionManagementService).findAll(captor.capture(), any());
        assertThat(captor.getValue().getFilters())
                .containsEntry("role.roleCode", "ADMIN_DIOCESE")
                .containsEntry("effect", "ALLOW");
    }

    private String token(List<String> roles, List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal("user", "LEADER1", roles, permissions));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
