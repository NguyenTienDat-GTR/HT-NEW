package phucuong.ht.modules.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.service.SiteLoginGuard;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chạy với Postgres thật: truy vấn giáo phận theo subdomain được gọi ngoài transaction (như khi đăng nhập),
 * nên phải đọc hết kết quả trước khi trả về.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.site.base-domain=example.com",
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-site-test",
        "app.security.jwt.audience=ht-management-site-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.local.enabled=true",
        "app.media.local.dir=${java.io.tmpdir}/ht-media-site-it",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class SiteLoginGuardIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "site-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "site-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "site-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "site-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "site-rabbit");
        registry.add("spring.rabbitmq.password", () -> "site-rabbit-password");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DioceseRepository dioceseRepository;
    @Autowired private SiteLoginGuard siteLoginGuard;

    @Test
    void findsDioceseBySubdomainIgnoringCaseAndGuardRestrictsLoginToOwnDiocese() {
        UUID phuCuong = insertDiocese("Giáo phận Phú Cương (site test)", "phucuong");
        insertDiocese("Giáo phận Sài Gòn (site test)", "saigon");
        insertDiocese("Giáo phận không có trang riêng (site test)", null);
        jdbc.update("insert into account (username, created_at, status, password, auth_provider, must_change_password, diocese_id) "
                + "values ('site_test_pc', now(), true, 'x', 'LOCAL', false, ?)", phuCuong);
        jdbc.update("insert into account (username, created_at, status, password, auth_provider, must_change_password) "
                + "values ('site_test_global', now(), true, 'x', 'LOCAL', false)");

        assertEquals(phuCuong, dioceseRepository.findBySiteSubdomain("PhuCuong").orElseThrow().getId());
        assertTrue(dioceseRepository.findBySiteSubdomain("khong-ton-tai").isEmpty());

        assertDoesNotThrow(() -> siteLoginGuard.assertCanLogin("site_test_pc", "phucuong.example.com"));
        ApiException rejected = assertThrows(ApiException.class,
                () -> siteLoginGuard.assertCanLogin("site_test_pc", "saigon.example.com"));
        assertEquals(HttpStatus.FORBIDDEN, rejected.getStatus());
        assertDoesNotThrow(() -> siteLoginGuard.assertCanLogin("site_test_global", "saigon.example.com"));
        assertDoesNotThrow(() -> siteLoginGuard.assertCanLogin("site_test_pc", "example.com"));
    }

    private UUID insertDiocese(String name, String subdomain) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into diocese (id, created_at, status, name, union_name, site_subdomain) values (?, now(), true, ?, 'Liên đoàn', ?)",
                id, name, subdomain);
        return id;
    }
}
