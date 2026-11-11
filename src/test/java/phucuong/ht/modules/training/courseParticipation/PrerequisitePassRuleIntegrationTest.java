package phucuong.ht.modules.training.courseParticipation;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "Đạt khóa tiên quyết" trên Postgres chạy riêng (không đụng DB dev): loại khóa có cấp chứng nhận tính theo chứng nhận được duyệt,
 * loại khóa không cấp chứng nhận tính theo kết quả khóa. Cùng một quy tắc cho điều kiện tham dự và số huynh trưởng đã đạt.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-prerequisite-test",
        "app.security.jwt.audience=ht-management-prerequisite-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.local.enabled=true",
        "app.media.local.dir=${java.io.tmpdir}/ht-media-prerequisite-it",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class PrerequisitePassRuleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "prerequisite-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "prerequisite-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "prerequisite-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "prerequisite-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "prerequisite-rabbit");
        registry.add("spring.rabbitmq.password", () -> "prerequisite-rabbit-password");
    }

    private static final String CERT_TYPE = "CO_CHUNG_NHAN_IT";
    private static final String PLAIN_TYPE = "KHONG_CHUNG_NHAN_IT";

    private static UUID dioceseId;
    private static UUID approved;
    private static UUID pending;
    private static UUID rejected;
    private static UUID revoked;
    private static UUID failed;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CourseParticipationRepository participations;
    @Autowired private TrainingCourseTypeRepository courseTypes;

    @Test
    @Order(1)
    void seed() {
        dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        UUID parishId = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'LĐ', now())", dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Bà Lụa', 'CĐ', ?, now())", parishId, deaneryId);
        approved = leader("approved", parishId);
        pending = leader("pending", parishId);
        rejected = leader("rejected", parishId);
        revoked = leader("revoked", parishId);
        failed = leader("failed", parishId);

        jdbc.update("insert into training_course_type (type_code, type_name, management_unit_type, diocese_id, has_certificate, created_at) "
                + "values (?, 'Có chứng nhận', 'DIOCESE', ?, true, now())", CERT_TYPE, dioceseId);
        jdbc.update("insert into training_course_type (type_code, type_name, management_unit_type, diocese_id, has_certificate, created_at) "
                + "values (?, 'Không chứng nhận', 'DIOCESE', ?, false, now())", PLAIN_TYPE, dioceseId);
        course("KH-CERT-IT", CERT_TYPE, 2024);
        course("KH-PLAIN-IT", PLAIN_TYPE, 2023);

        // Khóa có cấp chứng nhận: kết quả khóa đều Đạt, chỉ khác trạng thái chứng nhận.
        participation(approved, "KH-CERT-IT", "COMPLETED", "APPROVED", "2025-02-01T05:00:00Z");
        participation(pending, "KH-CERT-IT", "COMPLETED", "PENDING", null);
        participation(rejected, "KH-CERT-IT", "COMPLETED", "REJECTED", null);
        participation(revoked, "KH-CERT-IT", "COMPLETED", "REVOKED", "2025-02-01T05:00:00Z");
        // Khóa không cấp chứng nhận: chỉ xét kết quả khóa.
        participation(approved, "KH-PLAIN-IT", "COMPLETED", "NOT_REQUIRED", null);
        participation(failed, "KH-PLAIN-IT", "UNCOMPLETED", "NOT_REQUIRED", null);
    }

    @Test
    @Order(2)
    void certifiedTypeCountsOnlyApprovedCertificates() {
        assertEquals(Optional.of(2025), participations.findLatestCompletedYearByLeaderAndCourseType(approved, CERT_TYPE),
                "năm đạt là năm duyệt chứng nhận");
        assertEquals(Optional.empty(), participations.findLatestCompletedYearByLeaderAndCourseType(pending, CERT_TYPE));
        assertEquals(Optional.empty(), participations.findLatestCompletedYearByLeaderAndCourseType(rejected, CERT_TYPE));
        assertEquals(Optional.empty(), participations.findLatestCompletedYearByLeaderAndCourseType(revoked, CERT_TYPE));
    }

    @Test
    @Order(3)
    void uncertifiedTypeCountsTheCourseResult() {
        assertEquals(Optional.of(2023), participations.findLatestCompletedYearByLeaderAndCourseType(approved, PLAIN_TYPE),
                "năm đạt là năm tổ chức khóa");
        assertEquals(Optional.empty(), participations.findLatestCompletedYearByLeaderAndCourseType(failed, PLAIN_TYPE));
    }

    @Test
    @Order(4)
    void prerequisitePickerCountsLeadersWithTheSameRule() {
        Map<String, Long> counts = courseTypes.countCompletedLeadersByType(dioceseId);
        assertEquals(1L, counts.get(CERT_TYPE));
        assertEquals(1L, counts.get(PLAIN_TYPE));
    }

    private UUID leader(String key, UUID parishId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, gender, leader_level, parish_id) "
                + "values (?, now(), true, 'Gioan', ?, 'Test', '1990-01-01', ?, 'NAM', 'NONE', ?)", id, key, key + "@example.com", parishId);
        return id;
    }

    private void course(String code, String typeCode, int hostYear) {
        jdbc.update("insert into training_course (course_code, course_name, host_year, location, start_date, end_date, course_type_code, diocese_id, passing_score, created_at) "
                        + "values (?, ?, ?, 'Tòa giám mục', ?::date, ?::date, ?, ?, 5, now())",
                code, "Khóa " + code, hostYear, hostYear + "-06-01", hostYear + "-06-05", typeCode, dioceseId);
    }

    private void participation(UUID leaderId, String courseCode, String result, String certificateStatus, String certificateApprovedAt) {
        jdbc.update("insert into course_participation (id, leader_id, course_id, approval_status, participation_status, eligible, result, "
                        + "certificate_approval_status, certificate_approved_at, created_at, created_by) "
                        + "values (?, ?, ?, 'APPROVED', 'ATTENDED', true, ?, ?, ?::timestamptz, now(), 'seed')",
                UUID.randomUUID().toString(), leaderId, courseCode, result, certificateStatus, certificateApprovedAt);
    }
}
