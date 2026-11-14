package phucuong.ht.modules.mobile.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tắt {@code app.mobile.auth.enabled}: ba endpoint đăng nhập của app di động không còn tồn tại. */
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.mobile.auth.enabled=false"
})
@AutoConfigureMockMvc
class MobileAuthDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsNotFoundWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isNotFound());
    }
}
