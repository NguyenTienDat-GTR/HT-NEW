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
import phucuong.ht.modules.system.model.enums.PermissionEffect;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideCreateRequest;
import phucuong.ht.modules.system.model.response.AccountPermissionOverrideResponse;
import phucuong.ht.modules.system.service.AccountPermissionOverrideManagementService;

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
class AccountPermissionOverrideControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private AccountPermissionOverrideManagementService accountPermissionOverrideManagementService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowDioceseAdminManageAccountPermission() throws Exception {
        AccountPermissionOverrideCreateRequest request = new AccountPermissionOverrideCreateRequest();
        request.setUsername("target");
        request.setPermissionCode("organization.position.read.diocese");
        request.setEffect(PermissionEffect.DENY);

        when(accountPermissionOverrideManagementService.create(any(AccountPermissionOverrideCreateRequest.class), any()))
                .thenReturn(AccountPermissionOverrideResponse.builder()
                        .id("apo-1")
                        .username("target")
                        .permissionCode("organization.position.read.diocese")
                        .effect(PermissionEffect.DENY)
                        .status(true)
                        .build());

        mockMvc.perform(post("/api/system/account-permissions")
                        .header("Authorization", bearer(token(List.of("ADMIN_DIOCESE"), List.of("system.account_permission.manage.diocese"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("apo-1"));
    }

    @Test
    void shouldRejectAccountPermissionManageWithoutAuthority() throws Exception {
        AccountPermissionOverrideCreateRequest request = new AccountPermissionOverrideCreateRequest();
        request.setUsername("target");
        request.setPermissionCode("organization.position.read.diocese");
        request.setEffect(PermissionEffect.DENY);

        mockMvc.perform(post("/api/system/account-permissions")
                        .header("Authorization", bearer(token(List.of("ADMIN_DIOCESE"), List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private String token(List<String> roles, List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal("user", "LEADER1", roles, permissions));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
