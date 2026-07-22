package phucuong.ht.modules.system.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.service.PermissionManagementService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PermissionControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private PermissionManagementService permissionManagementService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldBindPermissionEffectFilterFromJsonFilters() throws Exception {
        when(permissionManagementService.findAll(any(BaseSearchRequest.class), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/system/permissions")
                        .param("page", "0")
                        .param("size", "10")
                        .param("filters", "{\"module.moduleCode\":\"system\",\"effect\":\"ALLOW\"}")
                        .header("Authorization", bearer(token(List.of("ADMIN_DIOCESE"), List.of("system.role_permission.read.diocese")))))
                .andExpect(status().isOk());

        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(permissionManagementService).findAll(captor.capture(), any());
        assertThat(captor.getValue().getFilters())
                .containsEntry("module.moduleCode", "system")
                .containsEntry("effect", "ALLOW");
    }

    private String token(List<String> roles, List<String> permissions) {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal("user", "LEADER1", roles, permissions));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
