package phucuong.ht.modules.badge;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.badge.config.model.request.BadgeConfigUpsertRequest;
import phucuong.ht.modules.badge.config.model.response.BadgeConfigResponse;
import phucuong.ht.modules.badge.config.service.BadgeConfigService;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;
import phucuong.ht.modules.badge.export.service.BadgeExportService;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.training.courseAttendance.service.AttendanceTokenService;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingBulkApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Kiểm tra xuyên suốt trên Postgres/Redis/RabbitMQ chạy riêng (không đụng DB dev):
 * migration V101, thư viện cấu hình thẻ lưu JSONB, xuất PDF thẻ từ dữ liệu thật, điểm danh bằng QR ký số,
 * gửi email thông tin tham dự (ghi nhận hồ sơ thiếu / sai email, không gửi trùng) và tự gửi khi hồ sơ được duyệt.
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
        "app.security.jwt.issuer=ht-management-badge-test",
        "app.security.jwt.audience=ht-management-badge-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.local.enabled=true",
        "app.media.local.dir=${java.io.tmpdir}/ht-media-badge-it",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class AttendeeBadgeAttendanceInvitationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "badge-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "badge-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "badge-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "badge-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "badge-rabbit");
        registry.add("spring.rabbitmq.password", () -> "badge-rabbit-password");
    }

    private static final String COURSE = "KH-IT-1";
    private static final String OTHER_COURSE = "KH-IT-2";
    private static final String ADMIN = "gh.admin";
    private static final String OTHER_ADMIN = "gh.other";

    private static UUID dioceseId;
    private static UUID deaneryId;
    private static UUID otherDeaneryId;
    private static final Map<String, String> PARTICIPATION = new java.util.LinkedHashMap<>();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private BadgeConfigService badgeConfigService;
    @Autowired private phucuong.ht.modules.media.service.MediaAssetService mediaAssetService;
    @Autowired private BadgeExportService badgeExportService;
    @Autowired private AttendanceTokenService tokenService;
    @Autowired private CourseParticipationService participationService;
    @MockitoBean private JavaMailSender mailSender;

    @BeforeEach
    void stubMailSender() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private static Authentication principal(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static Authentication deaneryAdmin() {
        return principal(ADMIN, "training.participation.approve.deanery", "training.participation.read.deanery", "training.attendance.scan.deanery",
                "training.badge_config.read.deanery", "training.badge_config.create.deanery",
                "training.badge_config.update.deanery", "training.badge_config.delete.deanery");
    }

    private static Authentication superAdmin() {
        return principal("superadmin", "ROLE_SUPER_ADMIN", "training.badge_config.read.diocese");
    }

    // ---------- dữ liệu ----------

    private void insertLeader(UUID id, String holy, String last, String first, String email, UUID parishId, String phone) {
        jdbc.update("insert into leader (id, holy_name, last_name, first_name, birth_date, email, phone_number, gender, leader_level, parish_id, created_at) "
                + "values (?, ?, ?, ?, '1995-03-07', ?, ?, 'NAM', 'NONE', ?, now())", id, holy, last, first, email, phone, parishId);
    }

    private void insertParticipation(String key, UUID leaderId, String courseCode, String team, String approval, String status) {
        String id = UUID.randomUUID().toString();
        PARTICIPATION.put(key, id);
        jdbc.update("insert into course_participation (id, leader_id, course_id, team_name, approval_status, participation_status, eligible, created_at, created_by) "
                + "values (?, ?, ?, ?, ?, ?, true, now(), 'seed')", id, leaderId, courseCode, team, approval, status);
    }

    private void setFlags(String courseCode, boolean attendance, boolean autoSend) {
        jdbc.update("update training_course set attendance_enabled=?, auto_send_invitation=? where course_code=?", attendance, autoSend, courseCode);
    }

    private void resetParticipantState() {
        jdbc.update("update course_participation set checked_in_at=null, checked_in_by=null, invitation_sent_at=null, invitation_error=null");
    }

    @Test
    @Order(1)
    void seedData() {
        dioceseId = UUID.randomUUID();
        deaneryId = UUID.randomUUID();
        otherDeaneryId = UUID.randomUUID();
        UUID parishA = UUID.randomUUID();
        UUID parishB = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'Liên đoàn PC', now())", dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Bến Cát', 'HĐ', ?, now())", otherDeaneryId, dioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Bà Lụa', 'CĐ', ?, now())", parishA, deaneryId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Búng', 'CĐ', ?, now())", parishB, deaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values (?, now(), ?)", ADMIN, deaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values (?, now(), ?)", OTHER_ADMIN, otherDeaneryId);

        jdbc.update("insert into training_course_type (type_code, type_name, management_unit_type, diocese_id, created_at) values ('DU_TRUONG_IT', 'Dự trưởng', 'DEANERY', ?, now())", dioceseId);
        for (String code : new String[]{COURSE, OTHER_COURSE}) {
            jdbc.update("insert into training_course (course_code, course_name, host_year, location, start_date, end_date, course_type_code, diocese_id, deanery_id, passing_score, created_at) "
                    + "values (?, ?, 2026, 'Nhà thờ Bà Lụa', ?, ?, 'DU_TRUONG_IT', ?, ?, 5, now())",
                    code, "Khóa " + code, LocalDate.now().minusDays(1), LocalDate.now().plusDays(7), dioceseId, deaneryId);
        }

        UUID an = UUID.randomUUID();
        UUID binh = UUID.randomUUID();
        UUID chi = UUID.randomUUID();
        UUID dung = UUID.randomUUID();
        UUID em = UUID.randomUUID();
        UUID giang = UUID.randomUUID();
        UUID hanh = UUID.randomUUID();
        insertLeader(an, "Phêrô", "Nguyễn Văn", "An", "an@example.com", parishA, "0900000001");
        insertLeader(binh, "Maria", "Trần Thị", "Bình", "binh@example.com", parishA, "0900000002");
        insertLeader(chi, "Giuse", "Lê", "Chi", "khong-phai-email", parishB, "0900000003");
        insertLeader(dung, "Anna", "Phạm", "Dung", "", parishB, "0900000004");
        insertLeader(em, "Phaolô", "Võ", "Em", "em@example.com", parishB, "0900000005");
        insertLeader(giang, "Luca", "Đỗ", "Giang", "giang@example.com", parishA, "0900000006");
        insertLeader(hanh, "Marta", "Hồ", "Hạnh", "hanh@example.com", parishA, "0900000007");

        insertParticipation("an", an, COURSE, "Đội 1", "APPROVED", "ATTENDED");
        insertParticipation("binh", binh, COURSE, "Đội 1", "APPROVED", "ATTENDED");
        insertParticipation("chi", chi, COURSE, "Đội 2", "APPROVED", "ATTENDED");
        insertParticipation("dung", dung, COURSE, "Đội 2", "APPROVED", "ATTENDED");
        insertParticipation("em", em, COURSE, null, "APPROVED", "ATTENDED");
        insertParticipation("giang", giang, COURSE, null, "PENDING", "REGISTERED");
        insertParticipation("hanh", hanh, COURSE, null, "REJECTED", "REJECTED");
        insertParticipation("an2", an, OTHER_COURSE, "Đội 1", "APPROVED", "ATTENDED");

        assertEquals(8, jdbc.queryForObject("select count(*) from course_participation", Integer.class));
    }

    // ---------- migration ----------

    @Test
    @Order(2)
    void migrationAddsColumnsTableAndPermissions() {
        assertEquals("NO", jdbc.queryForObject("select is_nullable from information_schema.columns where table_name='training_course' and column_name='attendance_enabled'", String.class));
        assertEquals("NO", jdbc.queryForObject("select is_nullable from information_schema.columns where table_name='training_course' and column_name='auto_send_invitation'", String.class));
        for (String column : new String[]{"checked_in_at", "checked_in_by", "invitation_sent_at", "invitation_error"}) {
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_name='course_participation' and column_name=?", Integer.class, column), column);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_name='badge_config'", Integer.class));
        // Khóa tạo trước khi có tính năng mặc định tắt cả hai cờ.
        assertEquals(0, jdbc.queryForObject("select count(*) from training_course where attendance_enabled or auto_send_invitation", Integer.class));

        assertEquals(8, jdbc.queryForObject("select count(*) from permission where resource_code='badge_config'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from permission where resource_code='attendance' and action_code='scan'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from role_permission where role_code='ADMIN_DEANERY' and permission_code='training.attendance.scan.deanery'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from role_permission where role_code='ADMIN_DEANERY' and permission_code='training.attendance.scan.diocese'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from permission_resource where resource_code='badge_config'", Integer.class));
        assertEquals(4, countRolePermissions("ADMIN_DIOCESE", "diocese"));
        assertEquals(4, countRolePermissions("SUPER_ADMIN", "diocese"));
        assertEquals(4, countRolePermissions("ADMIN_DEANERY", "deanery"));
        assertEquals(0, countRolePermissions("ADMIN_DEANERY", "diocese"));
        assertEquals(0, countRolePermissions("ADMIN_PARISH", "deanery"));
    }

    private int countRolePermissions(String role, String scope) {
        return jdbc.queryForObject("select count(*) from role_permission rp join permission p on p.permission_code = rp.permission_code "
                + "where rp.role_code = ? and p.resource_code='badge_config' and p.scope_code = ? and rp.status = true", Integer.class, role, scope);
    }

    // ---------- thư viện cấu hình thẻ trên DB thật ----------

    private CertificateLayoutItem field(String key, double x, double y) {
        CertificateLayoutItem item = new CertificateLayoutItem();
        item.setType("FIELD");
        item.setFieldKey(key);
        item.setX(x);
        item.setY(y);
        item.setFontSize(0.09);
        item.setAlign("center");
        item.setBold(true);
        return item;
    }

    private CertificateLayoutItem qr(double x, double y) {
        CertificateLayoutItem item = new CertificateLayoutItem();
        item.setType("QR");
        item.setX(x);
        item.setY(y);
        item.setSize(0.4);
        return item;
    }

    private BadgeConfigUpsertRequest badgeRequest(String name) {
        BadgeConfigUpsertRequest request = new BadgeConfigUpsertRequest();
        request.setName(name);
        request.setCardWidthMm(90.0);
        request.setCardHeightMm(55.0);
        request.setHasBack(true);
        request.setFrontLayout(List.of(field("leaderFullName", 0.5, 0.35), field("teamName", 0.5, 0.62), qr(0.86, 0.5)));
        CertificateLayoutItem text = new CertificateLayoutItem();
        text.setType("TEXT");
        text.setText("Mang theo thẻ khi điểm danh");
        text.setX(0.5);
        text.setY(0.85);
        text.setFontSize(0.07);
        text.setAlign("center");
        request.setBackLayout(List.of(field("courseName", 0.5, 0.2), text));
        return request;
    }

    private static UUID deaneryConfigId;

    private static byte[] backgroundPng() {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(120, 80, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Order(3)
    void badgeConfigRoundTripsJsonbAndEnforcesUniqueness() {
        // Ảnh nền thật: tải lên qua dịch vụ media (đúng người tải) rồi gắn vào cấu hình khi lưu.
        UUID frontAsset = mediaAssetService.upload(backgroundPng(), phucuong.ht.modules.media.model.enums.MediaCategory.BADGE_BACKGROUND,
                new phucuong.ht.common.utils.securityUtils().currentAuditActor(deaneryAdmin())).getId();
        BadgeConfigUpsertRequest createRequest = badgeRequest("Thẻ chuẩn");
        createRequest.setFrontBackgroundAssetId(frontAsset);
        BadgeConfigResponse created = badgeConfigService.create(createRequest, deaneryAdmin());
        deaneryConfigId = created.getId();
        assertEquals("DEANERY", created.getScopeLevel());
        assertTrue(created.getIsDefault());
        assertEquals(10, created.getCardsPerPage());

        BadgeConfigResponse loaded = badgeConfigService.get(deaneryConfigId, deaneryAdmin());
        assertEquals(3, loaded.getFrontLayout().size());
        assertEquals("leaderFullName", loaded.getFrontLayout().get(0).getFieldKey());
        assertEquals("QR", loaded.getFrontLayout().get(2).getType());
        assertEquals(0.4, loaded.getFrontLayout().get(2).getSize());
        assertEquals("Mang theo thẻ khi điểm danh", loaded.getBackLayout().get(1).getText());
        assertTrue(loaded.getHasBack());
        assertEquals(frontAsset, loaded.getFrontBackgroundAssetId());
        assertTrue(loaded.getFrontBackground().startsWith("/api/media/" + frontAsset + "/preview?sig="));
        assertEquals(90.0, loaded.getCardWidthMm());
        assertEquals(55.0, loaded.getCardHeightMm());

        // Chỉ mục duy nhất: một cấu hình chung và một tên (không phân biệt hoa thường) mỗi phạm vi.
        String scopeKey = "DEANERY:" + deaneryId;
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into badge_config (id, name, is_default, scope_key, card_width_mm, card_height_mm, created_at) "
                + "values (?, 'Khác', true, ?, 90, 55, now())", UUID.randomUUID(), scopeKey));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into badge_config (id, name, is_default, scope_key, card_width_mm, card_height_mm, created_at) "
                + "values (?, 'THẺ CHUẨN', false, ?, 90, 55, now())", UUID.randomUUID(), scopeKey));
        // Cùng tên ở phạm vi khác thì được.
        jdbc.update("insert into badge_config (id, name, is_default, scope_key, card_width_mm, card_height_mm, created_at) "
                + "values (?, 'Thẻ chuẩn', false, 'SYSTEM', 90, 55, now())", UUID.randomUUID());
    }

    @Test
    @Order(4)
    void defaultCanMoveAndOnlyNonDefaultCanBeDeleted() {
        BadgeConfigResponse second = badgeConfigService.create(badgeRequest("Thẻ thứ hai"), deaneryAdmin());
        assertFalse(second.getIsDefault());
        assertEquals("Thẻ chuẩn (1)", badgeConfigService.create(badgeRequest("Thẻ chuẩn"), deaneryAdmin()).getName());

        assertTrue(badgeConfigService.setDefault(second.getId(), deaneryAdmin()).getIsDefault());
        assertFalse(badgeConfigService.get(deaneryConfigId, deaneryAdmin()).getIsDefault());
        assertThrows(ApiException.class, () -> badgeConfigService.delete(second.getId(), deaneryAdmin()));
        badgeConfigService.setDefault(deaneryConfigId, deaneryAdmin());
        badgeConfigService.delete(second.getId(), deaneryAdmin());
        assertEquals(0, jdbc.queryForObject("select count(*) from badge_config where id = ?", Integer.class, second.getId()));
    }

    @Test
    @Order(5)
    void configsOfOtherScopesStayInvisible() {
        // Cấu hình SYSTEM chèn ở trên được giáo hạt dùng/xem nhưng không sửa được.
        List<BadgeConfigResponse> options = badgeConfigService.options(deaneryAdmin());
        assertTrue(options.stream().anyMatch(c -> "SYSTEM".equals(c.getScopeLevel()) && !c.getEditable()));
        UUID systemId = options.stream().filter(c -> "SYSTEM".equals(c.getScopeLevel())).findFirst().orElseThrow().getId();
        assertEquals(403, assertThrows(ApiException.class, () -> badgeConfigService.update(systemId, badgeRequest("x"), deaneryAdmin())).getStatus().value());

        Authentication otherDeanery = principal(OTHER_ADMIN, "training.badge_config.read.deanery");
        List<BadgeConfigResponse> theirs = badgeConfigService.options(otherDeanery);
        assertTrue(theirs.stream().noneMatch(c -> "DEANERY".equals(c.getScopeLevel())));
    }

    // ---------- xuất thẻ ----------

    @Test
    @Order(6)
    void exportsOnlyApprovedParticipantsToPdf() throws Exception {
        BadgeExportService.PdfExport export = badgeExportService.export(COURSE, new BadgeExportRequest(null, null, null, null, null));

        // 5 hồ sơ đã duyệt (không tính chờ duyệt, bị từ chối, khóa khác): 1 trang mặt trước + 1 trang mặt sau.
        assertEquals(5, export.badgeCount());
        assertEquals(2, export.pageCount());
        try (PDDocument document = Loader.loadPDF(export.content())) {
            assertEquals(2, document.getNumberOfPages());
        }
        assertTrue(export.filename().startsWith("Thẻ tham dự - Khóa KH-IT-1"));

        BadgeExportService.PdfExport team = badgeExportService.export(COURSE, new BadgeExportRequest(null, null, "đội 2", false, null));
        assertEquals(2, team.badgeCount());
        assertEquals(1, team.pageCount());
    }

    @Test
    @Order(7)
    void exportEndpointRunsAsBackgroundJobAndServesThePdf() throws Exception {
        MvcResult submitted = mockMvc.perform(post("/api/training/courses/" + COURSE + "/badges/export/jobs")
                        .with(authentication(deaneryAdmin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Đội 1\",\"includeBack\":true,\"flipEdge\":\"LONG\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        String jobId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.jobId");
        String status = awaitJobStatus("/api/training/courses/" + COURSE + "/badges/export/jobs/" + jobId, deaneryAdmin());
        assertEquals("DONE", status);

        // Job của khóa này không đọc được qua đường dẫn của khóa khác.
        MvcResult wrongCourse = mockMvc.perform(get("/api/training/courses/" + OTHER_COURSE + "/badges/export/jobs/" + jobId).with(authentication(deaneryAdmin())))
                .andReturn();
        assertEquals("FAILED", JsonPath.read(wrongCourse.getResponse().getContentAsString(), "$.data.status"));
        mockMvc.perform(get("/api/training/courses/" + OTHER_COURSE + "/badges/export/jobs/" + jobId + "/file").with(authentication(deaneryAdmin())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());

        MvcResult file = mockMvc.perform(get("/api/training/courses/" + COURSE + "/badges/export/jobs/" + jobId + "/file").with(authentication(deaneryAdmin())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"))
                .andReturn();
        assertEquals("%PDF", new String(file.getResponse().getContentAsByteArray(), 0, 4));
        // Tải xong thì job bị xóa.
        mockMvc.perform(get("/api/training/courses/" + COURSE + "/badges/export/jobs/" + jobId + "/file").with(authentication(deaneryAdmin())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @Order(7)
    void usableConfigsEndpointListsConfigsForTheCourseWithoutNeedingConfigPermissions() throws Exception {
        Authentication participantsOnly = principal(ADMIN, "training.participation.read.deanery");
        MvcResult result = mockMvc.perform(get("/api/training/courses/" + COURSE + "/badges/configs").with(authentication(participantsOnly))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        List<String> names = JsonPath.read(body, "$.data[*].name");
        assertTrue(names.contains("Thẻ chuẩn"), names.toString());
        List<Boolean> applied = JsonPath.read(body, "$.data[*].applied");
        assertEquals(1, applied.stream().filter(Boolean::booleanValue).count());
        mockMvc.perform(get("/api/training/courses/" + COURSE + "/badges/configs").with(authentication(principal(OTHER_ADMIN, "training.participation.read.deanery"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(get("/api/training/courses/" + COURSE + "/badges/configs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @Order(8)
    void exportJobReportsFailureForMissingConfigurationOrSelection() throws Exception {
        MvcResult submitted = mockMvc.perform(post("/api/training/courses/" + COURSE + "/badges/export/jobs")
                        .with(authentication(deaneryAdmin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Đội không có\"}"))
                .andReturn();
        String jobId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.jobId");
        assertEquals("FAILED", awaitJobStatus("/api/training/courses/" + COURSE + "/badges/export/jobs/" + jobId, deaneryAdmin()));
        MvcResult status = mockMvc.perform(get("/api/training/courses/" + COURSE + "/badges/export/jobs/" + jobId).with(authentication(deaneryAdmin()))).andReturn();
        assertTrue(((String) JsonPath.read(status.getResponse().getContentAsString(), "$.data.error")).contains("Không có hồ sơ nào phù hợp"));
    }

    @Test
    @Order(9)
    void endpointsRejectAnonymousUsersAndUsersOutsideTheCoursesUnit() throws Exception {
        String url = "/api/training/courses/" + COURSE + "/badges/export/jobs";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        // Admin giáo hạt khác: có quyền nhưng không thuộc giáo hạt tổ chức khóa.
        Authentication otherDeanery = principal(OTHER_ADMIN, "training.participation.approve.deanery", "training.participation.read.deanery");
        mockMvc.perform(post(url).with(authentication(otherDeanery)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        // Thiếu quyền đọc hồ sơ tham dự.
        Authentication noPermission = principal(ADMIN, "organization.leader.read.deanery");
        mockMvc.perform(post(url).with(authentication(noPermission)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(post("/api/training/courses/KHONG-CO/badges/export/jobs").with(authentication(deaneryAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        // Đọc được nhưng không quản lý: không gửi email / quét được.
        Authentication readOnly = principal(ADMIN, "training.participation.read.deanery");
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/invitations/send").with(authentication(readOnly))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/scan").with(authentication(readOnly))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/summary").with(authentication(readOnly)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    // ---------- điểm danh ----------

    private org.springframework.test.web.servlet.ResultActions scan(String courseCode, String token) throws Exception {
        return mockMvc.perform(post("/api/training/courses/" + courseCode + "/attendance/scan").with(authentication(deaneryAdmin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    @Test
    @Order(10)
    void scanningRequiresAttendanceToBeEnabled() throws Exception {
        setFlags(COURSE, false, false);
        scan(COURSE, tokenService.tokenFor(COURSE, PARTICIPATION.get("an")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertNull(jdbc.queryForObject("select checked_in_at from course_participation where id = ?", java.time.OffsetDateTime.class, PARTICIPATION.get("an")));
    }

    @Test
    @Order(11)
    void scanChecksInOnceAndPersists() throws Exception {
        setFlags(COURSE, true, false);
        String token = tokenService.tokenFor(COURSE, PARTICIPATION.get("an"));

        MvcResult first = scan(COURSE, token).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String body = first.getResponse().getContentAsString();
        assertEquals("CHECKED_IN", JsonPath.read(body, "$.data.status"));
        assertEquals("Phêrô", JsonPath.read(body, "$.data.holyName"));
        assertEquals("Bà Lụa", JsonPath.read(body, "$.data.parishName"));
        assertEquals("Đội 1", JsonPath.read(body, "$.data.teamName"));
        assertEquals(ADMIN, jdbc.queryForObject("select checked_in_by from course_participation where id = ?", String.class, PARTICIPATION.get("an")));
        java.time.OffsetDateTime firstTime = jdbc.queryForObject("select checked_in_at from course_participation where id = ?", java.time.OffsetDateTime.class, PARTICIPATION.get("an"));
        assertNotNull(firstTime);

        MvcResult second = scan(COURSE, token).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        assertEquals("ALREADY_CHECKED_IN", JsonPath.read(second.getResponse().getContentAsString(), "$.data.status"));
        assertEquals(firstTime.toInstant(), jdbc.queryForObject("select checked_in_at from course_participation where id = ?", java.time.OffsetDateTime.class, PARTICIPATION.get("an")).toInstant());
    }

    @Test
    @Order(116)
    void scanPermissionIsSeparateFromApprovalAndScopedToTheCourseUnit() throws Exception {
        String token = tokenService.tokenFor(COURSE, PARTICIPATION.get("binh"));
        String body = "{\"token\":\"" + token + "\"}";
        var forbidden = org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden();
        Authentication approveOnly = principal(ADMIN, "training.participation.approve.deanery", "training.participation.read.deanery");
        for (String path : new String[]{"lookup", "scan"}) {
            mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/" + path).with(authentication(approveOnly))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(forbidden);
        }
        mockMvc.perform(put("/api/training/courses/" + COURSE + "/attendance/participants/" + PARTICIPATION.get("binh")).with(authentication(approveOnly))
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":true}")).andExpect(forbidden);
        // Chỉ có quyền quét (không duyệt đăng ký) vẫn quét được khóa của đúng đơn vị; đơn vị khác thì không.
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/lookup").with(authentication(principal(ADMIN, "training.attendance.scan.deanery")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/lookup").with(authentication(principal(OTHER_ADMIN, "training.attendance.scan.deanery")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(forbidden);
        // Quyền cấp giáo phận không dùng được cho khóa do giáo hạt quản lý.
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/lookup").with(authentication(principal(ADMIN, "training.attendance.scan.diocese")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(forbidden);
    }

    @Test
    @Order(117)
    void participantDetailEndpointReturnsAttendanceEmailAndActors() throws Exception {
        jdbc.update("update course_participation set checked_in_at=now(), checked_in_by=? where id=?", ADMIN, PARTICIPATION.get("binh"));
        String url = "/api/training/courses/" + COURSE + "/attendance/participants/" + PARTICIPATION.get("binh");
        MvcResult r = mockMvc.perform(get(url).with(authentication(principal(ADMIN, "training.participation.read.deanery")))).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        String body = r.getResponse().getContentAsString();
        assertEquals("Maria", JsonPath.read(body, "$.data.holyName"));
        assertEquals("binh@example.com", JsonPath.read(body, "$.data.email"));
        assertEquals(ADMIN, JsonPath.read(body, "$.data.checkedInBy"));
        assertEquals("seed", JsonPath.read(body, "$.data.createdBy"));
        assertEquals("Phú Cường", JsonPath.read(body, "$.data.dioceseName"));
        mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants/" + PARTICIPATION.get("an2"))
                .with(authentication(principal(ADMIN, "training.participation.read.deanery"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(get(url).with(authentication(principal(OTHER_ADMIN, "training.participation.read.deanery"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        jdbc.update("update course_participation set checked_in_at=null, checked_in_by=null where id=?", PARTICIPATION.get("binh"));
    }

    @Test
    @Order(115)
    void lookupDoesNotCheckInAndScanDoes() throws Exception {
        setFlags(COURSE, true, false);
        String token = tokenService.tokenFor(COURSE, PARTICIPATION.get("binh"));
        MvcResult r = mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/lookup").with(authentication(deaneryAdmin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertEquals("READY", JsonPath.read(r.getResponse().getContentAsString(), "$.data.status"));
        assertNull(jdbc.queryForObject("select checked_in_at from course_participation where id = ?", java.time.OffsetDateTime.class, PARTICIPATION.get("binh")));
        scan(COURSE, token).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/lookup").with(authentication(principal(OTHER_ADMIN, "training.participation.approve.deanery")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        jdbc.update("update course_participation set checked_in_at=null, checked_in_by=null where id = ?", PARTICIPATION.get("binh"));
    }

    @Test
    @Order(12)
    void scanRejectsForgedWrongCourseAndRemovedParticipants() throws Exception {
        scan(COURSE, "HTATT1." + PARTICIPATION.get("binh") + ".AAAAAAAAAAAAAAAAAAAAAA")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        scan(COURSE, "https://example.com/certificates/ABC")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        // Mã hợp lệ của khóa khác.
        scan(COURSE, tokenService.tokenFor(COURSE, PARTICIPATION.get("an2")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        // Hồ sơ chờ duyệt / bị từ chối không điểm danh được.
        scan(COURSE, tokenService.tokenFor(COURSE, PARTICIPATION.get("giang")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        scan(COURSE, tokenService.tokenFor(COURSE, PARTICIPATION.get("hanh")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/attendance/scan").with(authentication(deaneryAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("select count(*) from course_participation where checked_in_at is not null", Integer.class));
    }

    @Test
    @Order(13)
    void manualCheckInUndoAndSummary() throws Exception {
        String url = "/api/training/courses/" + COURSE + "/attendance/participants/" + PARTICIPATION.get("binh");
        mockMvc.perform(put(url).with(authentication(deaneryAdmin())).contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("CHECKED_IN"));
        mockMvc.perform(put(url).with(authentication(deaneryAdmin())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        MvcResult summary = mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/summary").with(authentication(deaneryAdmin()))).andReturn();
        String body = summary.getResponse().getContentAsString();
        assertEquals(5, (int) JsonPath.read(body, "$.data.totalParticipants"));
        assertEquals(2, (int) JsonPath.read(body, "$.data.checkedIn"));
        assertEquals(3, (int) JsonPath.read(body, "$.data.notCheckedIn"));
        assertEquals(2, (int) JsonPath.read(body, "$.data.withoutEmail"));

        mockMvc.perform(put(url).with(authentication(deaneryAdmin())).contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":false}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("CHECK_IN_CANCELLED"));
        assertNull(jdbc.queryForObject("select checked_in_at from course_participation where id = ?", java.time.OffsetDateTime.class, PARTICIPATION.get("binh")));
    }

    // ---------- email thông tin tham dự ----------

    private String sendInvitations(String body) throws Exception {
        MvcResult submitted = mockMvc.perform(post("/api/training/courses/" + COURSE + "/invitations/send")
                        .with(authentication(deaneryAdmin())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String jobId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.jobId");
        assertEquals("DONE", awaitJobStatus("/api/training/courses/" + COURSE + "/invitations/jobs/" + jobId, deaneryAdmin()));
        return mockMvc.perform(get("/api/training/courses/" + COURSE + "/invitations/jobs/" + jobId).with(authentication(deaneryAdmin())))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Order(14)
    void sendsInvitationsNotesMissingAndBadEmailsAndKeepsGoing() throws Exception {
        resetParticipantState();
        org.mockito.Mockito.clearInvocations(mailSender);

        String body = sendInvitations("{\"includeQr\":true}");

        assertEquals(5, (int) JsonPath.read(body, "$.data.result.total"));
        assertEquals(3, (int) JsonPath.read(body, "$.data.result.sent"));
        assertEquals(2, (int) JsonPath.read(body, "$.data.result.failed"));
        assertEquals(true, JsonPath.read(body, "$.data.result.includeQr"));
        List<String> reasons = JsonPath.read(body, "$.data.result.issues[*].reason");
        assertTrue(reasons.stream().anyMatch(r -> r.contains("Email không hợp lệ: khong-phai-email")), reasons.toString());
        assertTrue(reasons.stream().anyMatch(r -> r.equals("Hồ sơ chưa có email")), reasons.toString());
        verify(mailSender, times(3)).send(any(MimeMessage.class));

        assertNotNull(sentAt("an"));
        assertNotNull(sentAt("binh"));
        assertNotNull(sentAt("em"));
        assertNull(sentAt("chi"));
        assertNull(sentAt("dung"));
        assertTrue(error("chi").startsWith("Email không hợp lệ"));
        assertEquals("Hồ sơ chưa có email", error("dung"));
        assertNull(error("an"));
    }

    @Test
    @Order(15)
    void secondSendSkipsAlreadySentAndRetriesFailuresOnly() throws Exception {
        org.mockito.Mockito.clearInvocations(mailSender);
        String body = sendInvitations("{\"includeQr\":true}");

        assertEquals(3, (int) JsonPath.read(body, "$.data.result.skippedAlreadySent"));
        assertEquals(0, (int) JsonPath.read(body, "$.data.result.sent"));
        assertEquals(2, (int) JsonPath.read(body, "$.data.result.failed"));
        verify(mailSender, never()).send(any(MimeMessage.class));

        // Sửa email lỗi rồi gửi lại: chỉ hồ sơ đó được gửi, lỗi cũ được xóa.
        jdbc.update("update leader set email='chi@example.com' where phone_number='0900000003'");
        String fixed = sendInvitations("{\"includeQr\":true}");
        assertEquals(1, (int) JsonPath.read(fixed, "$.data.result.sent"));
        assertEquals(1, (int) JsonPath.read(fixed, "$.data.result.failed"));
        assertNotNull(sentAt("chi"));
        assertNull(error("chi"));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @Order(16)
    void resendAndSelectionAndQrRules() throws Exception {
        org.mockito.Mockito.clearInvocations(mailSender);
        String body = sendInvitations("{\"participationIds\":[\"" + PARTICIPATION.get("an") + "\"],\"resend\":true,\"includeQr\":false}");
        assertEquals(1, (int) JsonPath.read(body, "$.data.result.sent"));
        assertEquals(false, JsonPath.read(body, "$.data.result.includeQr"));
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("an@example.com", sent.getValue().getAllRecipients()[0].toString());

        // Khóa không bật điểm danh: xin QR bị từ chối ngay, không tạo job.
        setFlags(COURSE, false, false);
        mockMvc.perform(post("/api/training/courses/" + COURSE + "/invitations/send").with(authentication(deaneryAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"includeQr\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        setFlags(COURSE, true, false);
        // Hồ sơ ngoài danh sách tham dự bị ghi nhận lỗi, không làm hỏng cả lần gửi.
        String mixed = sendInvitations("{\"participationIds\":[\"" + PARTICIPATION.get("em") + "\",\"" + PARTICIPATION.get("giang") + "\"],\"resend\":true}");
        assertEquals(1, (int) JsonPath.read(mixed, "$.data.result.sent"));
        assertEquals(1, (int) JsonPath.read(mixed, "$.data.result.failed"));
    }

    // ---------- tự động gửi khi duyệt ----------

    private <T> void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(message);
    }

    private void setApproval(String key, String approval, String status) {
        jdbc.update("update course_participation set approval_status=?, participation_status=?, invitation_sent_at=null, invitation_error=null where id=?",
                approval, status, PARTICIPATION.get(key));
    }

    @Test
    @Order(17)
    void approvalDoesNotSendWhenAutoSendIsOff() throws Exception {
        setFlags(COURSE, true, false);
        setApproval("giang", "PENDING", "REGISTERED");
        org.mockito.Mockito.clearInvocations(mailSender);

        TrainingApprovalRequest request = new TrainingApprovalRequest();
        request.setApproved(true);
        participationService.deaneryApprove(PARTICIPATION.get("giang"), request, deaneryAdmin());

        assertEquals("APPROVED", jdbc.queryForObject("select approval_status from course_participation where id=?", String.class, PARTICIPATION.get("giang")));
        Thread.sleep(1500);
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertNull(sentAt("giang"));
    }

    @Test
    @Order(18)
    void approvalSendsInvitationWithQrWhenAutoSendIsOn() throws Exception {
        setFlags(COURSE, true, true);
        setApproval("giang", "PENDING", "REGISTERED");
        org.mockito.Mockito.clearInvocations(mailSender);

        TrainingApprovalRequest request = new TrainingApprovalRequest();
        request.setApproved(true);
        participationService.deaneryApprove(PARTICIPATION.get("giang"), request, deaneryAdmin());

        awaitTrue(() -> sentAt("giang") != null, "Email thông tin tham dự chưa được gửi sau khi duyệt");
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sent.capture());
        assertEquals("giang@example.com", sent.getValue().getAllRecipients()[0].toString());
        sent.getValue().saveChanges();
        assertTrue(sent.getValue().getContentType().toLowerCase().contains("multipart"));
        assertNull(error("giang"));
    }

    @Test
    @Order(19)
    void bulkApprovalSendsOnlyToNewlyApprovedAndSkipsRejections() throws Exception {
        setFlags(COURSE, false, true);
        setApproval("giang", "PENDING", "REGISTERED");
        setApproval("hanh", "PENDING", "REGISTERED");
        jdbc.update("update course_participation set eligible = true where id in (?, ?)", PARTICIPATION.get("giang"), PARTICIPATION.get("hanh"));
        org.mockito.Mockito.clearInvocations(mailSender);

        TrainingBulkApprovalRequest request = new TrainingBulkApprovalRequest();
        request.setIds(List.of(PARTICIPATION.get("giang"), PARTICIPATION.get("hanh")));
        request.setApproved(true);
        participationService.bulkApprove(request, deaneryAdmin());

        awaitTrue(() -> sentAt("giang") != null && sentAt("hanh") != null, "Duyệt hàng loạt chưa gửi email cho cả hai hồ sơ");
        // Khóa không bật điểm danh: vẫn gửi thông tin nhưng không kèm QR.
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        for (MimeMessage message : sent.getAllValues()) {
            message.saveChanges();
            assertFalse(contains(message, "image/png"));
        }

        // Từ chối thì không gửi gì.
        setApproval("giang", "PENDING", "REGISTERED");
        org.mockito.Mockito.clearInvocations(mailSender);
        TrainingApprovalRequest reject = new TrainingApprovalRequest();
        reject.setApproved(false);
        reject.setReview("Không đủ điều kiện");
        participationService.deaneryApprove(PARTICIPATION.get("giang"), reject, deaneryAdmin());
        Thread.sleep(1500);
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertNull(sentAt("giang"));
        assertEquals("REJECTED", jdbc.queryForObject("select approval_status from course_participation where id=?", String.class, PARTICIPATION.get("giang")));
    }

    @Test
    @Order(20)
    void autoSendDoesNotResendToAlreadyInvitedAndNotesBadEmails() throws Exception {
        setFlags(COURSE, true, true);
        // Hồ sơ có email lỗi được duyệt: ghi lại lỗi để người dùng thấy, không ném lỗi ra luồng duyệt.
        jdbc.update("update leader set email='sai-dinh-dang' where phone_number='0900000003'");
        setApproval("chi", "PENDING", "REGISTERED");
        org.mockito.Mockito.clearInvocations(mailSender);

        TrainingApprovalRequest request = new TrainingApprovalRequest();
        request.setApproved(true);
        participationService.deaneryApprove(PARTICIPATION.get("chi"), request, deaneryAdmin());

        awaitTrue(() -> error("chi") != null, "Lỗi email của hồ sơ chưa được ghi lại");
        assertTrue(error("chi").startsWith("Email không hợp lệ: sai-dinh-dang"));
        assertNull(sentAt("chi"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @Order(21)
    void removingAParticipantClearsTheirCheckInAndInvitationState() {
        jdbc.update("update course_participation set team_name='Đội 9', checked_in_at=now(), checked_in_by='x', invitation_sent_at=now(), invitation_error='lỗi cũ' where id=?", PARTICIPATION.get("em"));

        participationService.removeParticipant(PARTICIPATION.get("em"), deaneryAdmin());

        Map<String, Object> row = jdbc.queryForMap("select approval_status, team_name, checked_in_at, checked_in_by, invitation_sent_at, invitation_error "
                + "from course_participation where id=?", PARTICIPATION.get("em"));
        assertEquals("REJECTED", row.get("approval_status"));
        assertNull(row.get("team_name"));
        assertNull(row.get("checked_in_at"));
        assertNull(row.get("checked_in_by"));
        assertNull(row.get("invitation_sent_at"));
        assertNull(row.get("invitation_error"));
    }

    @Autowired private phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService participantImportService;

    private static java.nio.file.Path teamImportFile(List<String[]> rows) throws Exception {
        List<String> headers = List.of("Email", "Số điện thoại", "Tên thánh", "Họ và tên", "Ngày sinh", "Giới tính", "Cấp độ", "Giáo hạt", "Giáo xứ", "Tên đội");
        List<phucuong.ht.common.excel.ExcelColumn<String[]>> columns = new java.util.ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int index = i;
            columns.add(phucuong.ht.common.excel.ExcelColumn.of(headers.get(i), row -> row[index].isEmpty() ? null : row[index]));
        }
        byte[] content = phucuong.ht.common.excel.ExcelExportWriter.write("Dữ liệu", columns, rows);
        java.nio.file.Path path = java.nio.file.Files.createTempFile("team-import", ".xlsx");
        java.nio.file.Files.write(path, content);
        path.toFile().deleteOnExit();
        return path;
    }

    private void importTeam(List<String[]> rows) throws Exception {
        try {
            participantImportService.importParticipants("KH-IT-3", teamImportFile(rows), deaneryAdmin(),
                    phucuong.ht.common.excel.job.JobProgress.NONE, true, null);
        } catch (phucuong.ht.common.excel.ImportValidationException exception) {
            throw new AssertionError("Import lỗi: " + exception.getErrors().stream()
                    .map(error -> error.row() + ":" + error.column() + ":" + error.message()).toList(), exception);
        }
    }

    @Test
    @Order(22)
    void importIntoACourseThatHasAlreadyStartedIsRejected() throws Exception {
        // COURSE bắt đầu từ hôm qua: không nhập Excel thêm vào danh sách tham dự nữa.
        phucuong.ht.common.exception.ApiException thrown = org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.common.exception.ApiException.class,
                () -> participantImportService.precheck(COURSE, deaneryAdmin(), true));
        assertTrue(thrown.getMessage().contains("đã diễn ra"), thrown.getMessage());
        org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.common.exception.ApiException.class,
                () -> participantImportService.precheck(COURSE, deaneryAdmin()));
    }

    @Test
    @Order(22)
    void importedTeamParticipantsAreInvitedWhenAutoSendIsOn() throws Exception {
        jdbc.update("insert into training_course (course_code, course_name, host_year, start_date, end_date, course_type_code, diocese_id, deanery_id, passing_score, "
                        + "attendance_enabled, auto_send_invitation, created_at) values ('KH-IT-3', 'Khóa nhập đội', 2026, ?, ?, 'DU_TRUONG_IT', ?, ?, 5, true, true, now())",
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(7), dioceseId, deaneryId);
        org.mockito.Mockito.clearInvocations(mailSender);

        importTeam(List.<String[]>of(
                new String[]{"an@example.com", "0900000001", "Phêrô", "Nguyễn Văn An", "07/03/1995", "Nam", "", "Thủ Dầu Một", "Bà Lụa", "Đội A"},
                new String[]{"binh@example.com", "0900000002", "Maria", "Trần Thị Bình", "07/03/1995", "Nam", "", "Thủ Dầu Một", "Bà Lụa", "Đội B"}));

        assertEquals(2, jdbc.queryForObject("select count(*) from course_participation where course_id='KH-IT-3' and approval_status='APPROVED'", Integer.class));
        awaitTrue(() -> jdbc.queryForObject("select count(*) from course_participation where course_id='KH-IT-3' and invitation_sent_at is not null", Integer.class) == 2,
                "Import danh sách đội chưa gửi email thông tin tham dự");
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        List<String> to = sent.getAllValues().stream().map(message -> {
            try {
                return message.getAllRecipients()[0].toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).sorted().toList();
        assertEquals(List.of("an@example.com", "binh@example.com"), to);
    }

    @Test
    @Order(23)
    void importedParticipantsAreNotInvitedWhenAutoSendIsOff() throws Exception {
        jdbc.update("update training_course set auto_send_invitation = false where course_code = 'KH-IT-3'");
        jdbc.update("delete from course_participation where course_id = 'KH-IT-3'");
        org.mockito.Mockito.clearInvocations(mailSender);

        importTeam(List.<String[]>of(
                new String[]{"em@example.com", "0900000005", "Phaolô", "Võ Em", "07/03/1995", "Nam", "", "Thủ Dầu Một", "Búng", "Đội A"}));

        assertEquals(1, jdbc.queryForObject("select count(*) from course_participation where course_id='KH-IT-3'", Integer.class));
        Thread.sleep(1500);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private MvcResult listParticipants(String query, Authentication authentication) throws Exception {
        return mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants" + query).with(authentication(authentication))).andReturn();
    }

    private String searchParticipants(String term) throws Exception {
        return mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants").param("search", term).param("size", "100")
                .with(authentication(deaneryAdmin()))).andReturn().getResponse().getContentAsString();
    }

    @Test
    @Order(24)
    void participantListFiltersAndPaginatesOnTheServer() throws Exception {
        jdbc.update("update course_participation set checked_in_at=null, checked_in_by=null where course_id='KH-IT-1'");
        jdbc.update("update course_participation set checked_in_at=now(), checked_in_by='gh.admin' where id=?", PARTICIPATION.get("an"));
        jdbc.update("update course_participation set invitation_sent_at=now(), invitation_error=null where id=?", PARTICIPATION.get("an"));
        jdbc.update("update course_participation set invitation_sent_at=null, invitation_error='Email không hợp lệ: sai-dinh-dang' where id=?", PARTICIPATION.get("chi"));

        MvcResult all = listParticipants("?size=100", deaneryAdmin());
        assertEquals(200, all.getResponse().getStatus());
        String body = all.getResponse().getContentAsString();
        int total = JsonPath.read(body, "$.data.totalElements");
        assertTrue(total >= 3, "phải có các hồ sơ đã duyệt của khóa: " + total);
        List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        assertFalse(ids.contains(PARTICIPATION.get("an2")), "hồ sơ của khóa khác không được lẫn vào");
        assertTrue(ids.contains(PARTICIPATION.get("an")));

        String checkedBody = listParticipants("?checkedIn=true", deaneryAdmin()).getResponse().getContentAsString();
        assertEquals(List.of(PARTICIPATION.get("an")), JsonPath.<List<String>>read(checkedBody, "$.data.content[*].id"));
        assertEquals("gh.admin", JsonPath.<List<String>>read(checkedBody, "$.data.content[*].checkedInBy").get(0));
        String notChecked = listParticipants("?checkedIn=false&size=100", deaneryAdmin()).getResponse().getContentAsString();
        assertEquals(total - 1, (int) JsonPath.read(notChecked, "$.data.totalElements"));

        String failed = listParticipants("?invitation=FAILED", deaneryAdmin()).getResponse().getContentAsString();
        assertTrue(JsonPath.<List<String>>read(failed, "$.data.content[*].id").contains(PARTICIPATION.get("chi")));
        // Mọi dòng trả về đều phải có lỗi gửi (kể cả hồ sơ thiếu email đã được ghi nhận ở các lần gửi trước).
        assertEquals(JsonPath.<List<String>>read(failed, "$.data.content[*].id").size(), JsonPath.<List<String>>read(failed, "$.data.content[*].invitationError").size());
        assertTrue(JsonPath.<List<String>>read(failed, "$.data.content[*].email").contains("sai-dinh-dang"));

        String noEmail = listParticipants("?invitation=NO_EMAIL&size=100", deaneryAdmin()).getResponse().getContentAsString();
        List<String> noEmailIds = JsonPath.read(noEmail, "$.data.content[*].id");
        assertTrue(noEmailIds.contains(PARTICIPATION.get("dung")));

        String byName = searchParticipants("phêrô");
        assertTrue(JsonPath.<List<String>>read(byName, "$.data.content[*].id").contains(PARTICIPATION.get("an")));
        String accentless = searchParticipants("ba lua");
        assertTrue(JsonPath.<List<String>>read(accentless, "$.data.content[*].id").contains(PARTICIPATION.get("an")));

        String paged = listParticipants("?size=1&page=1", deaneryAdmin()).getResponse().getContentAsString();
        assertEquals(1, ((List<?>) JsonPath.read(paged, "$.data.content")).size());
        assertEquals(total, (int) JsonPath.read(paged, "$.data.totalElements"));

        mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants?invitation=BAD").with(authentication(deaneryAdmin())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @Order(25)
    void participantListIsLimitedToTheCoursesOwnUnit() throws Exception {
        Authentication otherDeanery = principal(OTHER_ADMIN, "training.participation.read.deanery");
        assertEquals(403, listParticipants("", otherDeanery).getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/api/training/courses/" + COURSE + "/attendance/participants")).andReturn().getResponse().getStatus());
        Authentication readOnly = principal(ADMIN, "training.participation.read.deanery");
        assertEquals(200, listParticipants("", readOnly).getResponse().getStatus());
    }

    // ---------- tiện ích ----------

    private java.time.OffsetDateTime sentAt(String key) {
        return jdbc.queryForObject("select invitation_sent_at from course_participation where id=?", java.time.OffsetDateTime.class, PARTICIPATION.get(key));
    }

    private String error(String key) {
        return jdbc.queryForObject("select invitation_error from course_participation where id=?", String.class, PARTICIPATION.get(key));
    }

    private static boolean contains(jakarta.mail.Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return true;
        }
        if (part.isMimeType("multipart/*")) {
            jakarta.mail.Multipart multipart = (jakarta.mail.Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                if (contains(multipart.getBodyPart(i), mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String awaitJobStatus(String url, Authentication authentication) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        String status = "PENDING";
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get(url).with(authentication(authentication))).andReturn();
            status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
            if (!"PENDING".equals(status)) {
                return status;
            }
            Thread.sleep(200);
        }
        return status;
    }
}
