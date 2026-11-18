package phucuong.ht.modules.system.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.EndpointActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.IpActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Summary;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.TimelinePoint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class SystemMonitoringRepositoryTest {

    private static final OffsetDateTime TO = OffsetDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneOffset.ofHours(7));
    private static final OffsetDateTime FROM = TO.minusHours(6);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static JdbcTemplate jdbc;
    private static SystemMonitoringRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE system_log (
                    id VARCHAR(255) PRIMARY KEY, username VARCHAR(255), http_method VARCHAR(20), request_path VARCHAR(500),
                    status_code INTEGER, ip_address VARCHAR(45), duration_ms INTEGER, logged_at TIMESTAMPTZ NOT NULL
                )
                """);
        repository = new SystemMonitoringRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @BeforeEach
    void clearLogs() {
        jdbc.execute("TRUNCATE system_log");
    }

    @Test
    void summaryClassifiesRequestsInsideTheWindowOnly() {
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 100, TO.minusHours(1));
        log("binh", "POST", "/api/leaders", 500, "203.0.113.2", 300, TO.minusHours(1));
        log(null, "GET", "/api/leaders", 401, "172.21.0.4", 10, TO.minusHours(1));
        log("an", "GET", "/api/leaders", 403, "203.0.113.1", 10, TO.minusHours(1));
        log(null, "POST", "/api/auth/login", 401, "203.0.113.3", 10, TO.minusHours(1));
        log(null, "POST", "/api/mobile/auth/login", 400, "203.0.113.3", 10, TO.minusHours(1));
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 10, FROM.minusSeconds(1));
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 10, TO);

        Summary summary = repository.summary(FROM, TO);

        assertEquals(6, summary.totalRequests());
        assertEquals(1, summary.serverErrors());
        assertEquals(3, summary.deniedRequests(), "Gồm 401/403 của cả request đăng nhập");
        assertEquals(2, summary.failedLogins());
        assertEquals(4, summary.uniqueIps());
        assertEquals(2, summary.uniqueAccounts());
        assertEquals(1, summary.privateIpRequests());
        assertTrue(summary.p95Ms() >= 100);
    }

    @Test
    void monitoringPageOwnRequestsAreExcluded() {
        log("an", "GET", "/api/system/monitoring/overview", 200, "203.0.113.1", 10, TO.minusMinutes(1));

        assertEquals(0, repository.summary(FROM, TO).totalRequests());
        assertNull(repository.summary(FROM, TO).p95Ms());
        assertTrue(repository.topIps(FROM, TO, 5).isEmpty());
    }

    @Test
    void timelineFillsEmptyBuckets() {
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 10, TO.minusMinutes(30));

        List<TimelinePoint> timeline = repository.timeline(FROM, TO, 60);

        assertEquals(7, timeline.size());
        assertEquals(1, timeline.stream().mapToLong(TimelinePoint::requests).sum());
        assertEquals(0, timeline.get(0).requests());
        assertNull(timeline.get(0).p95Ms());
    }

    @Test
    void endpointsGroupIdentifiersIntoOnePath() {
        String id = UUID.randomUUID().toString();
        log("an", "GET", "/api/leaders/" + id, 200, "203.0.113.1", 10, TO.minusMinutes(3));
        log("an", "GET", "/api/leaders/" + UUID.randomUUID() + "/history", 200, "203.0.113.1", 10, TO.minusMinutes(3));
        log("an", "GET", "/api/training/courses/42", 200, "203.0.113.1", 900, TO.minusMinutes(2));
        log("an", "GET", "/api/training/courses/43", 500, "203.0.113.1", 900, TO.minusMinutes(2));
        log("an", "GET", "/api/leaders/" + UUID.randomUUID(), 200, "203.0.113.1", 10, TO.minusMinutes(1));

        List<EndpointActivity> busiest = repository.busiestEndpoints(FROM, TO, 5);
        assertEquals(List.of("/api/training/courses/{id}", "/api/leaders/{id}", "/api/leaders/{id}/history"),
                busiest.stream().map(EndpointActivity::path).toList());
        assertEquals(2, busiest.get(1).requests());

        List<EndpointActivity> slowest = repository.slowestEndpoints(FROM, TO, 2, 5);
        assertEquals("/api/training/courses/{id}", slowest.get(0).path());
        assertEquals(1, slowest.get(0).serverErrors());
        assertEquals(2, slowest.size(), "Đường dẫn ít mẫu bị loại");
    }

    @Test
    void suspiciousIpsMatchAnyThreshold() {
        for (int i = 0; i < 3; i++) {
            log(null, "POST", "/api/auth/login", 401, "203.0.113.7", 10, TO.minusMinutes(5));
        }
        log("an", "GET", "/api/leaders", 200, "203.0.113.8", 10, TO.minusMinutes(5));

        List<IpActivity> suspicious = repository.suspiciousIps(FROM, TO, 3, 100, 100, 5);

        assertEquals(1, suspicious.size());
        assertEquals("203.0.113.7", suspicious.get(0).ipAddress());
        assertEquals(3, suspicious.get(0).failedLogins());
    }

    @Test
    void averageCountsOnlyHoursWithData() {
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 10, TO.minusHours(5).withMinute(5));
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 10, TO.minusHours(5).withMinute(10));
        log("an", "GET", "/api/leaders", 200, "203.0.113.1", 10, TO.minusHours(2).withMinute(10));

        assertEquals(1.5, repository.averageRequestsPerActiveHour(FROM, TO));
        assertEquals(0, repository.averageRequestsPerActiveHour(FROM.minusDays(2), FROM.minusDays(1)));
    }

    private static void log(String username, String method, String path, int status, String ip, int durationMs, OffsetDateTime at) {
        jdbc.update("""
                        INSERT INTO system_log (id, username, http_method, request_path, status_code, ip_address, duration_ms, logged_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), username, method, path, status, ip, durationMs, at);
    }
}
