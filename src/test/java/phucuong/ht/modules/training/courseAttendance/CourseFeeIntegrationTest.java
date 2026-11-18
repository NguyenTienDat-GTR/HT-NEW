package phucuong.ht.modules.training.courseAttendance;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lệ phí khóa huấn luyện trên Postgres/Redis/RabbitMQ chạy riêng (không đụng DB dev): ràng buộc migration V143, luật sửa lệ phí
 * (khóa sau khi có hồ sơ được duyệt đăng ký), quyền ghi nhận (quyền quét điểm danh), tính idempotent của việc ghi nhận đã đóng
 * và tải song song (nhiều người cùng bấm, bấm lặp) không lỗi và không đảo trạng thái.
 */
@Testcontainers
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-fee-test",
        "app.security.jwt.audience=ht-management-fee-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.local.enabled=true",
        "app.media.local.dir=${java.io.tmpdir}/ht-media-fee-it",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class CourseFeeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "fee-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "fee-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "fee-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "fee-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "fee-rabbit");
        registry.add("spring.rabbitmq.password", () -> "fee-rabbit-password");
    }

    private static final String COURSE = "KH-FEE-1";
    private static final String FREE_COURSE = "KH-FEE-FREE";
    private static final String NEW_COURSE = "KH-FEE-NEW";
    private static final String ADMIN = "gh.fee.admin";
    private static final String SECOND_ADMIN = "gh.fee.admin2";
    private static final String OTHER_ADMIN = "gh.fee.other";

    private static UUID dioceseId;
    private static UUID deaneryId;
    private static UUID otherDeaneryId;
    private static final Map<String, String> PARTICIPATION = new LinkedHashMap<>();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private JavaMailSender mailSender;

    @BeforeEach
    void stubMailSender() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private static Authentication principal(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    // Quyền quét điểm danh là quyền đánh dấu lệ phí; thêm quyền xem/sửa khóa để gọi API khóa.
    private static Authentication admin() {
        return principal(ADMIN, "training.attendance.scan.deanery", "training.participation.read.deanery",
                "training.course.read.deanery", "training.course.update.deanery", "training.course.create.deanery");
    }

    private void insertLeader(UUID id, String holy, String last, String first, UUID parishId, String phone) {
        jdbc.update("insert into leader (id, holy_name, last_name, first_name, birth_date, email, phone_number, gender, leader_level, parish_id, created_at) "
                + "values (?, ?, ?, ?, '1995-03-07', ?, ?, 'NAM', 'NONE', ?, now())", id, holy, last, first, first.toLowerCase() + "@example.com", phone, parishId);
    }

    private String insertParticipation(String key, UUID leaderId, String courseCode, String approval, String status) {
        String id = UUID.randomUUID().toString();
        PARTICIPATION.put(key, id);
        jdbc.update("insert into course_participation (id, leader_id, course_id, approval_status, participation_status, eligible, created_at, created_by) "
                + "values (?, ?, ?, ?, ?, true, now(), 'seed')", id, leaderId, courseCode, approval, status);
        return id;
    }

    private ResultActions markFee(Authentication who, String courseCode, String participationId, boolean paid) throws Exception {
        return mockMvc.perform(put("/api/training/courses/" + courseCode + "/attendance/participants/" + participationId + "/fee")
                .with(authentication(who)).contentType(MediaType.APPLICATION_JSON).content("{\"paid\":" + paid + "}"));
    }

    private ResultActions updateCourse(String courseCode, String body) throws Exception {
        return mockMvc.perform(put("/api/training/courses/" + courseCode).with(authentication(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private OffsetDateTime paidAt(String key) {
        return jdbc.queryForObject("select fee_paid_at from course_participation where id = ?", OffsetDateTime.class, PARTICIPATION.get(key));
    }

    private String paidBy(String key) {
        return jdbc.queryForObject("select fee_paid_by from course_participation where id = ?", String.class, PARTICIPATION.get(key));
    }

    private void insertCourse(String code, boolean fee) {
        jdbc.update("insert into training_course (course_code, course_name, host_year, location, start_date, end_date, course_type_code, diocese_id, deanery_id, passing_score, "
                        + "registration_enabled, fee_enabled, fee_amount, created_at) values (?, ?, 2026, 'Nhà thờ Bà Lụa', ?, ?, 'DU_TRUONG_FEE', ?, ?, 5, false, ?, ?, now())",
                code, "Khóa " + code, LocalDate.now().minusDays(1), LocalDate.now().plusDays(7), dioceseId, deaneryId, fee, fee ? 150_000L : null);
    }

    @Test
    @Order(1)
    void seedData() {
        dioceseId = UUID.randomUUID();
        deaneryId = UUID.randomUUID();
        otherDeaneryId = UUID.randomUUID();
        UUID parish = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'Liên đoàn PC', now())", dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Bến Cát', 'HĐ', ?, now())", otherDeaneryId, dioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Bà Lụa', 'CĐ', ?, now())", parish, deaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values (?, now(), ?)", ADMIN, deaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values (?, now(), ?)", OTHER_ADMIN, otherDeaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values (?, now(), ?)", SECOND_ADMIN, deaneryId);
        jdbc.update("insert into training_course_type (type_code, type_name, management_unit_type, diocese_id, created_at) values ('DU_TRUONG_FEE', 'Dự trưởng', 'DEANERY', ?, now())", dioceseId);

        insertCourse(COURSE, true);
        insertCourse(FREE_COURSE, false);
        insertCourse(NEW_COURSE, false);

        String[] names = {"An", "Binh", "Chi", "Dung", "Em"};
        for (int index = 0; index < names.length; index++) {
            UUID leader = UUID.randomUUID();
            insertLeader(leader, "Phêrô", "Nguyễn", names[index], parish, "09000001" + index + "0");
            insertParticipation(names[index].toLowerCase(), leader, COURSE, "APPROVED", "ATTENDED");
            if (index == 0) {
                insertParticipation("free", leader, FREE_COURSE, "APPROVED", "ATTENDED");
            }
        }
        UUID rejected = UUID.randomUUID();
        insertLeader(rejected, "Maria", "Trần", "Hanh", parish, "0900009999");
        insertParticipation("hanh", rejected, COURSE, "REJECTED", "REJECTED");
    }

    // ---------- migration ----------

    @Test
    @Order(2)
    void migrationAddsFeeColumnsAndKeepsOldCoursesFree() {
        assertEquals("NO", jdbc.queryForObject("select is_nullable from information_schema.columns where table_name='training_course' and column_name='fee_enabled'", String.class));
        assertEquals("YES", jdbc.queryForObject("select is_nullable from information_schema.columns where table_name='training_course' and column_name='fee_amount'", String.class));
        for (String column : new String[]{"fee_paid_at", "fee_paid_by"}) {
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_name='course_participation' and column_name=?", Integer.class, column), column);
        }
        assertEquals(2, jdbc.queryForObject("select count(*) from training_course where not fee_enabled", Integer.class));
    }

    @Test
    @Order(3)
    void databaseRejectsInconsistentFeeValues() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update training_course set fee_enabled = true, fee_amount = null where course_code = ?", NEW_COURSE));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update training_course set fee_enabled = true, fee_amount = 0 where course_code = ?", NEW_COURSE));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update training_course set fee_enabled = true, fee_amount = 1000000000 where course_code = ?", NEW_COURSE));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update training_course set fee_enabled = false, fee_amount = 5000 where course_code = ?", NEW_COURSE));
        assertEquals(1, jdbc.update("update training_course set fee_enabled = true, fee_amount = 999999999 where course_code = ?", NEW_COURSE));
        assertEquals(1, jdbc.update("update training_course set fee_enabled = false, fee_amount = null where course_code = ?", NEW_COURSE));
    }

    // ---------- tạo / sửa lệ phí ----------

    @Test
    @Order(4)
    void feeCanBeSetChangedAndClearedWhileNoRegistrationIsApproved() throws Exception {
        updateCourse(NEW_COURSE, "{\"feeEnabled\":true,\"feeAmount\":150000}").andExpect(status().isOk());
        assertEquals(150_000L, jdbc.queryForObject("select fee_amount from training_course where course_code = ?", Long.class, NEW_COURSE));

        String body = updateCourse(NEW_COURSE, "{\"feeAmount\":200000}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.TRUE, JsonPath.read(body, "$.data.feeEnabled"));
        assertEquals(200_000, ((Number) JsonPath.read(body, "$.data.feeAmount")).intValue());
        assertEquals(Boolean.FALSE, JsonPath.read(body, "$.data.feeLocked"));

        String cleared = updateCourse(NEW_COURSE, "{\"feeEnabled\":false}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.FALSE, JsonPath.read(cleared, "$.data.feeEnabled"));
        assertNull(JsonPath.read(cleared, "$.data.feeAmount"));
        assertNull(jdbc.queryForObject("select fee_amount from training_course where course_code = ?", Long.class, NEW_COURSE));
    }

    @Test
    @Order(5)
    void invalidFeeValuesAreRejectedWithVietnameseMessages() throws Exception {
        updateCourse(NEW_COURSE, "{\"feeEnabled\":true}").andExpect(status().isBadRequest());
        updateCourse(NEW_COURSE, "{\"feeEnabled\":true,\"feeAmount\":0}").andExpect(status().isBadRequest());
        updateCourse(NEW_COURSE, "{\"feeEnabled\":true,\"feeAmount\":-5}").andExpect(status().isBadRequest());
        updateCourse(NEW_COURSE, "{\"feeEnabled\":true,\"feeAmount\":1000000000}").andExpect(status().isBadRequest());
        assertFalse(jdbc.queryForObject("select fee_enabled from training_course where course_code = ?", Boolean.class, NEW_COURSE));
    }

    @Test
    @Order(6)
    void feeIsLockedOnceARegistrationHasBeenApproved() throws Exception {
        // COURSE đã có hồ sơ được duyệt: không đổi được cờ hay số tiền, gửi lại đúng giá trị cũ vẫn hợp lệ.
        updateCourse(COURSE, "{\"feeAmount\":999}").andExpect(status().isConflict());
        updateCourse(COURSE, "{\"feeEnabled\":false}").andExpect(status().isConflict());
        updateCourse(FREE_COURSE, "{\"feeEnabled\":true,\"feeAmount\":50000}").andExpect(status().isConflict());
        updateCourse(COURSE, "{\"feeEnabled\":true,\"feeAmount\":150000}").andExpect(status().isOk());
        assertEquals(150_000L, jdbc.queryForObject("select fee_amount from training_course where course_code = ?", Long.class, COURSE));
        assertFalse(jdbc.queryForObject("select fee_enabled from training_course where course_code = ?", Boolean.class, FREE_COURSE));
    }

    @Test
    @Order(7)
    void courseDetailReportsWhetherTheFeeIsLocked() throws Exception {
        String locked = mockMvc.perform(get("/api/training/courses/" + COURSE).with(authentication(admin()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.TRUE, JsonPath.read(locked, "$.data.feeLocked"));
        assertEquals(150_000, ((Number) JsonPath.read(locked, "$.data.feeAmount")).intValue());

        String open = mockMvc.perform(get("/api/training/courses/" + NEW_COURSE).with(authentication(admin()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.FALSE, JsonPath.read(open, "$.data.feeLocked"));
    }

    @Test
    @Order(8)
    void aRejectedButPaidRecordStillLocksTheFee() throws Exception {
        UUID leader = UUID.randomUUID();
        insertLeader(leader, "Giuse", "Lê", "Khoa", jdbc.queryForObject("select id from parish limit 1", UUID.class), "0900008888");
        String id = insertParticipation("khoa", leader, NEW_COURSE, "REJECTED", "REJECTED");
        updateCourse(NEW_COURSE, "{\"feeEnabled\":true,\"feeAmount\":100000}").andExpect(status().isOk());
        jdbc.update("update course_participation set fee_paid_at = now(), fee_paid_by = 'seed' where id = ?", id);
        updateCourse(NEW_COURSE, "{\"feeAmount\":120000}").andExpect(status().isConflict());
        assertEquals(100_000L, jdbc.queryForObject("select fee_amount from training_course where course_code = ?", Long.class, NEW_COURSE));
    }

    // ---------- ghi nhận đã đóng ----------

    @Test
    @Order(10)
    void markingFeeStoresTheActorAndTimeAndRepeatingItChangesNothing() throws Exception {
        String body = markFee(admin(), COURSE, PARTICIPATION.get("an"), true).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.TRUE, JsonPath.read(body, "$.data.feePaid"));
        assertEquals(ADMIN, paidBy("an"));
        OffsetDateTime first = paidAt("an");
        assertNotNull(first);

        // Gửi lặp bởi người khác: vẫn đã đóng, giữ nguyên thời điểm và người ghi nhận đầu tiên.
        markFee(principal(SECOND_ADMIN, "training.attendance.scan.deanery"), COURSE, PARTICIPATION.get("an"), true).andExpect(status().isOk());
        assertEquals(first.toInstant(), paidAt("an").toInstant());
        assertEquals(ADMIN, paidBy("an"));

        markFee(admin(), COURSE, PARTICIPATION.get("an"), false).andExpect(status().isOk());
        assertNull(paidAt("an"));
        assertNull(paidBy("an"));
        markFee(admin(), COURSE, PARTICIPATION.get("an"), false).andExpect(status().isOk());
        assertNull(paidAt("an"));
    }

    @Test
    @Order(11)
    void feePermissionIsTheAttendanceScanPermissionOfTheOwningUnit() throws Exception {
        String id = PARTICIPATION.get("binh");
        // Không có quyền quét điểm danh (chỉ duyệt / đọc) thì không ghi nhận được.
        markFee(principal(ADMIN, "training.participation.approve.deanery", "training.participation.read.deanery"), COURSE, id, true).andExpect(status().isForbidden());
        // Đơn vị khác và quyền cấp giáo phận không dùng được cho khóa do giáo hạt quản lý.
        markFee(principal(OTHER_ADMIN, "training.attendance.scan.deanery"), COURSE, id, true).andExpect(status().isForbidden());
        markFee(principal(ADMIN, "training.attendance.scan.diocese"), COURSE, id, true).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/training/courses/" + COURSE + "/attendance/participants/" + id + "/fee")
                .contentType(MediaType.APPLICATION_JSON).content("{\"paid\":true}")).andExpect(status().isUnauthorized());
        assertNull(paidAt("binh"));
    }

    @Test
    @Order(12)
    void feeIsRefusedWhenThePayloadOrTheTargetIsWrong() throws Exception {
        mockMvc.perform(put("/api/training/courses/" + COURSE + "/attendance/participants/" + PARTICIPATION.get("binh") + "/fee")
                .with(authentication(admin())).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        markFee(admin(), COURSE, "khong-ton-tai", true).andExpect(status().isNotFound());
        // Khóa không thu lệ phí.
        markFee(admin(), FREE_COURSE, PARTICIPATION.get("free"), true).andExpect(status().isConflict());
        // Hồ sơ của khóa khác, hồ sơ đã bị loại khỏi danh sách tham dự.
        markFee(admin(), COURSE, PARTICIPATION.get("free"), true).andExpect(status().isBadRequest());
        markFee(admin(), COURSE, PARTICIPATION.get("hanh"), true).andExpect(status().isConflict());
    }

    @Test
    @Order(13)
    void feeCanBeMarkedBeforeStartAndWithoutAttendanceButNotOnceTheCourseEnded() throws Exception {
        jdbc.update("update training_course set start_date = ?, end_date = ?, attendance_enabled = false, score_locked = true where course_code = ?",
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(5), COURSE);
        markFee(admin(), COURSE, PARTICIPATION.get("binh"), true).andExpect(status().isOk());

        jdbc.update("update training_course set finished_at = now(), finished_by = 'seed' where course_code = ?", COURSE);
        markFee(admin(), COURSE, PARTICIPATION.get("binh"), false).andExpect(status().isConflict());
        markFee(admin(), COURSE, PARTICIPATION.get("chi"), true).andExpect(status().isConflict());
        assertNotNull(paidAt("binh"));
        assertNull(paidAt("chi"));

        jdbc.update("update training_course set finished_at = null, finished_by = null, score_locked = false, start_date = ?, end_date = ? where course_code = ?",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(7), COURSE);
        jdbc.update("update course_participation set fee_paid_at = null, fee_paid_by = null where id = ?", PARTICIPATION.get("binh"));
    }

    @Test
    @Order(14)
    void feeIsRefusedWhenTheCourseIsDisabled() throws Exception {
        jdbc.update("update training_course set status = false where course_code = ?", COURSE);
        try {
            markFee(admin(), COURSE, PARTICIPATION.get("chi"), true).andExpect(status().isConflict());
        } finally {
            jdbc.update("update training_course set status = true where course_code = ?", COURSE);
        }
        assertNull(paidAt("chi"));
    }

    // ---------- tổng hợp ----------

    @Test
    @Order(20)
    void summaryAndListReportFeeFiguresOverTheCurrentParticipantsOnly() throws Exception {
        markFee(admin(), COURSE, PARTICIPATION.get("an"), true).andExpect(status().isOk());
        markFee(admin(), COURSE, PARTICIPATION.get("em"), true).andExpect(status().isOk());

        String summary = mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/summary").with(authentication(admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.TRUE, JsonPath.read(summary, "$.data.feeEnabled"));
        assertEquals(150_000, ((Number) JsonPath.read(summary, "$.data.feeAmount")).intValue());
        assertEquals(5, ((Number) JsonPath.read(summary, "$.data.totalParticipants")).intValue());
        assertEquals(750_000L, ((Number) JsonPath.read(summary, "$.data.feeTotal")).longValue());
        assertEquals(300_000L, ((Number) JsonPath.read(summary, "$.data.feePaidTotal")).longValue());
        assertEquals(450_000L, ((Number) JsonPath.read(summary, "$.data.feeRemaining")).longValue());
        assertEquals(2, ((Number) JsonPath.read(summary, "$.data.feePaidCount")).intValue());
        assertEquals(3, ((Number) JsonPath.read(summary, "$.data.feeUnpaidCount")).intValue());

        String unpaid = mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants?feePaid=false&size=50").with(authentication(admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(3, ((Number) JsonPath.read(unpaid, "$.data.totalElements")).intValue());
        String paid = mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants?feePaid=true&size=50").with(authentication(admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(2, ((Number) JsonPath.read(paid, "$.data.totalElements")).intValue());
        assertNotNull(JsonPath.read(paid, "$.data.content[0].feePaidAt"));
    }

    @Test
    @Order(21)
    void aPaidPersonWhoLeavesTheListIsNotCountedButKeepsThePaidStateWhenApprovedAgain() throws Exception {
        jdbc.update("update course_participation set approval_status = 'REJECTED', participation_status = 'REJECTED' where id = ?", PARTICIPATION.get("em"));
        String summary = mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/summary").with(authentication(admin())))
                .andReturn().getResponse().getContentAsString();
        assertEquals(4, ((Number) JsonPath.read(summary, "$.data.totalParticipants")).intValue());
        assertEquals(1, ((Number) JsonPath.read(summary, "$.data.feePaidCount")).intValue());
        markFee(admin(), COURSE, PARTICIPATION.get("em"), false).andExpect(status().isConflict());
        assertNotNull(paidAt("em"));

        jdbc.update("update course_participation set approval_status = 'APPROVED', participation_status = 'ATTENDED' where id = ?", PARTICIPATION.get("em"));
        String again = mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/summary").with(authentication(admin())))
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, ((Number) JsonPath.read(again, "$.data.feePaidCount")).intValue());
    }

    // ---------- tải song song ----------

    private List<Integer> runInParallel(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(task));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> fee(String key, boolean paid) {
        return () -> markFee(admin(), COURSE, PARTICIPATION.get(key), paid).andReturn().getResponse().getStatus();
    }

    @Test
    @Order(30)
    void manyRepeatedMarksOfTheSameRecordNeverFailOrMoveTheFirstTimestamp() throws Exception {
        jdbc.update("update course_participation set fee_paid_at = null, fee_paid_by = null where course_id = ?", COURSE);
        markFee(admin(), COURSE, PARTICIPATION.get("chi"), true).andExpect(status().isOk());
        OffsetDateTime first = paidAt("chi");

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            tasks.add(fee("chi", true));
        }
        assertTrue(runInParallel(tasks).stream().allMatch(code -> code == 200));
        assertEquals(first.toInstant(), paidAt("chi").toInstant());
        assertEquals(ADMIN, paidBy("chi"));
    }

    @Test
    @Order(31)
    void markingEveryParticipantAtOnceSucceedsForAll() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int round = 0; round < 8; round++) {
            for (String key : new String[]{"an", "binh", "chi", "dung", "em"}) {
                tasks.add(fee(key, true));
            }
        }
        assertTrue(runInParallel(tasks).stream().allMatch(code -> code == 200));
        assertEquals(5, jdbc.queryForObject("select count(*) from course_participation where course_id = ? and fee_paid_at is not null and fee_paid_by is not null", Integer.class, COURSE));
    }

    @Test
    @Order(32)
    void mixedConcurrentMarksAlwaysEndInAConsistentState() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            tasks.add(fee("dung", index % 2 == 0));
        }
        assertTrue(runInParallel(tasks).stream().allMatch(code -> code == 200));
        // Thời điểm và người ghi nhận luôn đi cùng nhau: hoặc cả hai có giá trị, hoặc cả hai rỗng.
        assertEquals(paidAt("dung") == null, paidBy("dung") == null);

        // Sau đó gửi trạng thái đích cuối cùng thì kết quả là đúng trạng thái đó, không phụ thuộc lịch sử.
        markFee(admin(), COURSE, PARTICIPATION.get("dung"), false).andExpect(status().isOk());
        assertNull(paidAt("dung"));
        markFee(admin(), COURSE, PARTICIPATION.get("dung"), true).andExpect(status().isOk());
        assertNotNull(paidAt("dung"));
    }
}
