package phucuong.ht.modules.system.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.service.AccountManagementService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerFilterBindingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private jwtUtils jwtUtils;

    @MockitoBean
    private AccountManagementService accountManagementService;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @BeforeEach
    void setUp() {
        when(redisTokenSessionService.isAccessTokenActive(anyString(), anyString(), anyString())).thenReturn(true);
        when(accountManagementService.findAll(any(BaseSearchRequest.class), any())).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void shouldBindRoleCodeFilterFromJsonFilters() throws Exception {
        mockMvc.perform(get("/api/system/accounts")
                        .param("page", "0")
                        .param("size", "10")
                        .param("filters", "{\"roleCode\":\"ADMIN_DIOCESE\"}")
                        .header("Authorization", bearer(token())))
                .andExpect(status().isOk());

        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(accountManagementService).findAll(captor.capture(), any());
        assertThat(captor.getValue().getFilters()).containsEntry("roleCode", "ADMIN_DIOCESE");
    }

    @Test
    void shouldBindDioceseIdFilterFromJsonFilters() throws Exception {
        UUID dioceseId = UUID.randomUUID();

        mockMvc.perform(get("/api/system/accounts")
                        .param("page", "0")
                        .param("size", "10")
                        .param("filters", "{\"dioceseId\":\"" + dioceseId + "\"}")
                        .header("Authorization", bearer(token())))
                .andExpect(status().isOk());

        ArgumentCaptor<BaseSearchRequest> captor = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(accountManagementService).findAll(captor.capture(), any());
        assertThat(captor.getValue().getFilters()).containsEntry("dioceseId", dioceseId.toString());
    }

    private String token() {
        return jwtUtils.generateAccessToken(AuthUserDetails.tokenPrincipal(
                "super-admin",
                null,
                List.of("SUPER_ADMIN"),
                List.of("system.account.manage.all")
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
