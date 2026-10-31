package phucuong.ht.modules.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import phucuong.ht.modules.auth.service.SiteHostPolicy;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SiteHostControllerTest {

    @Mock
    private SiteHostPolicy policy;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteHostController(policy)).build();
    }

    @Test
    void returns200ForServableHostAnd404Otherwise() throws Exception {
        when(policy.isServableDioceseHost("phucuong.example.com")).thenReturn(true);
        when(policy.isServableDioceseHost("khac.example.com")).thenReturn(false);

        mockMvc.perform(get("/internal/site-hosts/check").param("domain", "phucuong.example.com"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/site-hosts/check").param("domain", "khac.example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestsThatCameThroughTheProxyAreRejectedWithoutLookup() throws Exception {
        mockMvc.perform(get("/internal/site-hosts/check")
                        .param("domain", "phucuong.example.com")
                        .header("X-Site-Host", "example.com"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(policy);
    }
}
