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
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
import phucuong.ht.modules.system.service.AccountRoleService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountRoleControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private AccountRoleService accountRoleService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldAllowSuperAdminToToggleAccountRoleWithToggleAuthority() throws Exception {
        when(accountRoleService.toggleStatus(eq("ar-1"), any(StatusToggleRequest.class), any()))
                .thenReturn(AccountRoleResponse.builder()
                        .id("ar-1")
                        .username("target")
                        .roleCode("ADMIN_DIOCESE")
                        .status(false)
                        .build());

        mockMvc.perform(patch("/api/system/account-roles/ar-1/toggle-status")
                        .header("Authorization", bearer(token(
                                List.of("SUPER_ADMIN"),
                                List.of("system.account_role.toggle.all")
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toggleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ar-1"))
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void shouldRejectAccountRoleToggleWithoutAuthority() throws Exception {
        mockMvc.perform(patch("/api/system/account-roles/ar-1/toggle-status")
                        .header("Authorization", bearer(token(List.of("SUPER_ADMIN"), List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toggleRequest())))
                .andExpect(status().isForbidden());
    }

    private StatusToggleRequest toggleRequest() {
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        return request;
    }

    private String token(List<String> roles, List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal("user", null, roles, permissions));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
