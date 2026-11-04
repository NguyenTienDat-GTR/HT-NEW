package phucuong.ht.modules.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bằng chứng đồng ý xử lý dữ liệu cá nhân: ghi, tra cứu, rút; chính sách công khai; thiếu cờ đồng ý thì bị từ chối. */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-privacy-test",
        "app.security.jwt.audience=ht-management-privacy-client",
        "app.privacy.policy-version=9.9",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.local.enabled=true",
        "app.media.local.dir=${java.io.tmpdir}/ht-media-privacy-it",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class PrivacyConsentIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "privacy-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "privacy-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "privacy-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "privacy-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "privacy-rabbit");
        registry.add("spring.rabbitmq.password", () -> "privacy-rabbit-password");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private Authentication user(String username) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of("ADMIN_PARISH"), List.of());
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void policyVersionIsPublic() throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/privacy/policy")).andExpect(status().isOk()));
        assertEquals("9.9", data.get("policyVersion").asText());
    }

    @Test
    void statusRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me/privacy-consent")).andExpect(status().isUnauthorized());
    }

    @Test
    void consentIsRecordedWithEvidenceAndCanBeWithdrawn() throws Exception {
        Authentication me = user("privacy.user");

        assertFalse(data(mockMvc.perform(get("/api/auth/me/privacy-consent").with(authentication(me)))
                .andExpect(status().isOk())).get("consented").asBoolean());

        // Thiếu cờ hoặc cờ false: bị từ chối, không ghi gì.
        mockMvc.perform(post("/api/auth/me/privacy-consent").with(authentication(me)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/me/privacy-consent").with(authentication(me)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("privacyConsent", false))))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from privacy_consent where username = 'privacy.user'", Integer.class));

        JsonNode after = data(mockMvc.perform(post("/api/auth/me/privacy-consent").with(authentication(me))
                        .header("User-Agent", "privacy-it-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("privacyConsent", true))))
                .andExpect(status().isOk()));
        assertTrue(after.get("consented").asBoolean());
        assertEquals("9.9", after.get("consentedVersion").asText());

        Map<String, Object> row = jdbc.queryForMap("select * from privacy_consent where username = 'privacy.user'");
        assertEquals("SELF", row.get("consent_type"));
        assertEquals("LOGIN_GATE", row.get("source"));
        assertEquals("9.9", row.get("policy_version"));
        assertEquals("privacy-it-agent", row.get("user_agent"));
        assertNotNull(row.get("consented_at"));

        JsonNode withdrawn = data(mockMvc.perform(post("/api/auth/me/privacy-consent/withdraw").with(authentication(me)))
                .andExpect(status().isOk()));
        assertFalse(withdrawn.get("consented").asBoolean());
        assertNotNull(jdbc.queryForMap("select withdrawn_at from privacy_consent where username = 'privacy.user'").get("withdrawn_at"));
    }

    @Test
    void selfServiceEditsRequireConsentFlag() throws Exception {
        Authentication me = user("privacy.editor");
        mockMvc.perform(put("/api/auth/me/profile").with(authentication(me)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Nguyen Van A"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/auth/me/email").with(authentication(me)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "a@example.com", "currentPassword", "x"))))
                .andExpect(status().isBadRequest());
    }
}
