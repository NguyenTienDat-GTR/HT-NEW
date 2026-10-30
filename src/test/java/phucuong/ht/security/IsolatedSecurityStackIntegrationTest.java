package phucuong.ht.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.auth.service.TokenSessionState;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-isolated-test",
        "app.security.jwt.audience=ht-management-isolated-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false"
})
class IsolatedSecurityStackIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "isolated-redis-password")
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "isolated-rabbit-user")
            .withEnv("RABBITMQ_DEFAULT_PASS", "isolated-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configureIsolatedStack(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "isolated-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "isolated-rabbit-user");
        registry.add("spring.rabbitmq.password", () -> "isolated-rabbit-password");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Autowired
    private RedisTokenSessionService redisTokenSessionService;

    @Test
    void productionSwaggerAndOperationalMetricsRequireAuthentication() throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/swagger-ui/index.html").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/v3/api-docs").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/actuator/metrics").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/actuator/prometheus").getStatusCode());
    }

    @Test
    void crossOriginAndMalformedRequestsDoNotReachBusinessHandlers() {
        HttpHeaders corsHeaders = new HttpHeaders();
        corsHeaders.setOrigin("https://attacker.example");
        ResponseEntity<String> cors = restTemplate.exchange(
                new RequestEntity<>(corsHeaders, HttpMethod.OPTIONS, uri("/api/auth/login")), String.class);
        assertFalse(cors.getHeaders().containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> malformedJson = restTemplate.exchange(
                new RequestEntity<>("{\"username\":", jsonHeaders, HttpMethod.POST, uri("/api/auth/login")), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, malformedJson.getStatusCode());
    }

    @Test
    void refreshRotationIsAtomicAgainstConcurrentReuse() throws Exception {
        TokenSessionState session = redisTokenSessionService.createSession(
                "isolated-user-" + UUID.randomUUID(), "127.0.0.1", "access-1", "refresh-1", "refresh-token-1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(
                    pool.submit(() -> rotateAfter(start, session, "access-2", "refresh-2", "refresh-token-2")),
                    pool.submit(() -> rotateAfter(start, session, "access-3", "refresh-3", "refresh-token-3"))
            );
            start.countDown();
            long successes = results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count();
            assertEquals(1, successes);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refreshRotationSucceedsForTheCurrentToken() {
        TokenSessionState session = redisTokenSessionService.createSession(
                "isolated-user-" + UUID.randomUUID(), "127.0.0.1", "access-1", "refresh-1", "refresh-token-1");

        assertDoesNotThrow(() -> redisTokenSessionService.rotateRefreshSession(
                session.getSessionId(), session.getUsername(), "refresh-1", "refresh-token-1",
                "access-2", "refresh-2", "refresh-token-2"));
    }

    @Test
    void sessionPolicyKeepsAtMostTwoIpsAndOneSessionPerIp() {
        String username = "isolated-user-" + UUID.randomUUID();
        redisTokenSessionService.createSession(username, "127.0.0.1", "access-1", "refresh-1", "token-1");
        redisTokenSessionService.createSession(username, "127.0.0.2", "access-2", "refresh-2", "token-2");
        redisTokenSessionService.createSession(username, "127.0.0.1", "access-3", "refresh-3", "token-3");

        assertEquals(2, redisTokenSessionService.listActiveSessions(username).size());
        assertEquals(1, redisTokenSessionService.listActiveSessions(username).stream()
                .filter(session -> "127.0.0.1".equals(session.getIpAddress())).count());

        redisTokenSessionService.createSession(username, "127.0.0.3", "access-4", "refresh-4", "token-4");
        assertEquals(2, redisTokenSessionService.listActiveSessions(username).size());
        assertTrue(redisTokenSessionService.listActiveSessions(username).stream()
                .anyMatch(session -> "127.0.0.3".equals(session.getIpAddress())));
    }

    @Test
    void revokedSessionRejectsThePreviouslyIssuedAccessToken() {
        String username = "isolated-user-" + UUID.randomUUID();
        TokenSessionState session = redisTokenSessionService.createSession(
                username, "127.0.0.1", "access-1", "refresh-1", "refresh-token-1");

        redisTokenSessionService.revokeAllSessions(username);

        assertFalse(redisTokenSessionService.isAccessTokenActive(session.getSessionId(), username, "access-1"));
    }

    private boolean rotateAfter(CountDownLatch start, TokenSessionState session, String accessJti, String refreshJti, String refreshToken)
            throws InterruptedException {
        start.await();
        try {
            redisTokenSessionService.rotateRefreshSession(
                    session.getSessionId(), session.getUsername(), "refresh-1", "refresh-token-1",
                    accessJti, refreshJti, refreshToken);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity(uri(path), String.class);
    }

    private java.net.URI uri(String path) {
        return java.net.URI.create("http://localhost:" + port + path);
    }
}
