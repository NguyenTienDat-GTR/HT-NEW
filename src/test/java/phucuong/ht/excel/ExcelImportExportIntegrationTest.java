package phucuong.ht.excel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.common.excel.ExcelColumn;
import phucuong.ht.common.excel.ExcelExportWriter;
import phucuong.ht.common.excel.ExcelResponses;
import phucuong.ht.common.excel.ExcelSheetData;
import phucuong.ht.common.excel.ExcelWorkbookReader;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.excel.ImportValidationException;
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.certificate.model.enums.CertificateSignerType;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.service.CertificateService;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.trainingCourse.model.request.PastTrainingCourseRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;
import phucuong.ht.modules.training.trainingCourse.service.PastTrainingCourseService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra xuyên suốt trên Postgres/Redis/RabbitMQ chạy riêng (không đụng DB dev):
 * import huynh trưởng -> tạo khóa quá khứ -> import danh sách tham dự -> duyệt chứng nhận với ngày duyệt tự chọn
 * -> lịch sử cấp độ theo thứ tự cấp độ đã cấu hình -> xuất Excel.
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
        "app.security.jwt.issuer=ht-management-excel-test",
        "app.security.jwt.audience=ht-management-excel-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false"
})
class ExcelImportExportIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "excel-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "excel-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "excel-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "excel-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "excel-rabbit");
        registry.add("spring.rabbitmq.password", () -> "excel-rabbit-password");
    }

    private static final String USERNAME = "admin.phucuong";
    private static final String OTHER_USERNAME = "admin.xuanloc";
    private static UUID dioceseId;
    private static UUID otherDioceseId;
    private static String courseCode;
    private static String secondCourseCode;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.cache.CacheManager cacheManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private LeaderImportService leaderImportService;
    @Autowired private LeaderService leaderService;
    @Autowired private LeaderLevelService leaderLevelService;
    @Autowired private PastTrainingCourseService pastTrainingCourseService;
    @Autowired private PastCourseParticipantImportService participantImportService;
    @Autowired private CertificateService certificateService;
    @Autowired private phucuong.ht.modules.diocese.service.DioceseService dioceseService;
    @Autowired private phucuong.ht.modules.deanery.service.DeaneryService deaneryService;
    @Autowired private phucuong.ht.modules.parish.service.ParishService parishService;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private record Row(String... cells) {
    }

    // Dịch vụ import đọc file từ đĩa (tác vụ nền): ghi file upload mẫu ra file tạm.
    private static java.nio.file.Path path(MockMultipartFile file) {
        try {
            java.nio.file.Path path = java.nio.file.Files.createTempFile("excel-test", ".xlsx");
            java.nio.file.Files.write(path, file.getBytes());
            path.toFile().deleteOnExit();
            return path;
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Authentication admin(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static Authentication phuCuongAdmin() {
        return admin(USERNAME, "organization.leader.create.diocese", "organization.leader.read.diocese",
                "training.course.create_past.diocese", "training.certificate.approve.diocese");
    }

    private static MockMultipartFile excel(List<String> headers, List<Row> rows) {
        List<ExcelColumn<Row>> columns = new java.util.ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int index = i;
            columns.add(ExcelColumn.of(headers.get(i), row -> row.cells()[index].isEmpty() ? null : row.cells()[index]));
        }
        return new MockMultipartFile("file", "import.xlsx", ExcelResponses.XLSX.toString(), ExcelExportWriter.write("Dữ liệu", columns, rows));
    }

    private static MockMultipartFile imageZip(String name, byte[] content) {
        return imageZip(java.util.Map.of(name, content));
    }

    private static MockMultipartFile imageZip(java.util.Map<String, byte[]> entries) {
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
                for (var entry : entries.entrySet()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return new MockMultipartFile("images", "images.zip", "application/zip", output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static byte[] validPortrait() {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(60, 90, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setColor(java.awt.Color.BLUE);
            graphics.fillRect(0, 0, 60, 90);
            graphics.dispose();
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static final List<String> LEADER_HEADERS =
            List.of("Tên thánh *", "Họ và tên *", "Ngày sinh *", "Giới tính *", "Email *", "Số điện thoại *", "Cấp độ", "Giáo hạt *", "Giáo xứ *");

    @BeforeAll
    static void seedStatic() {
        // dữ liệu nền được tạo trong test đầu tiên để dùng được JdbcTemplate đã inject
    }

    @Test
    @Order(1)
    void seedOrganizationAndImportLeadersFromExcel() {
        dioceseId = UUID.randomUUID();
        otherDioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        UUID otherDeaneryId = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'Liên đoàn PC', now())", dioceseId);
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Xuân Lộc', 'Liên đoàn XL', now())", otherDioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Long Khánh', 'HĐ', ?, now())", otherDeaneryId, otherDioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (gen_random_uuid(), 'Bà Lụa', 'CĐ', ?, now())", deaneryId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (gen_random_uuid(), 'Búng', 'CĐ', ?, now())", deaneryId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (gen_random_uuid(), 'Gia Kiệm', 'CĐ', ?, now())", otherDeaneryId);
        jdbc.update("insert into account (username, created_at, diocese_id) values (?, now(), ?)", USERNAME, dioceseId);
        jdbc.update("insert into account (username, created_at, diocese_id) values (?, now(), ?)", OTHER_USERNAME, otherDioceseId);
        leaderLevelService.seedDefaults(dioceseId, "test");
        leaderLevelService.seedDefaults(otherDioceseId, "test");

        MockMultipartFile file = excel(LEADER_HEADERS, List.of(
                new Row("Phêrô", "Nguyễn Văn An", "07/03/1995", "Nam", "an@example.com", "0901234567", "Huynh trưởng xứ", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Maria", "Trần Thị Bình", "21/11/1998", "Nữ", "binh@example.com", "0901234568", "", "Thủ Dầu Một", "Búng"),
                new Row("Giuse", "Lê Văn Cường", "02/02/1990", "Nam", "cuong@example.com", "0901234569", "DU_TRUONG", "Thủ Dầu Một", "Bà Lụa")));
        ImportSummary summary = leaderImportService.importLeaders(path(file), phuCuongAdmin(), JobProgress.NONE);

        assertEquals(3, summary.created());
        assertEquals(3, jdbc.queryForObject("select count(*) from leader", Integer.class));
        assertEquals("HT_XU", jdbc.queryForObject("select leader_level from leader where email='an@example.com'", String.class));
        assertEquals("NONE", jdbc.queryForObject("select leader_level from leader where email='binh@example.com'", String.class));
        // Họ tên được tách như khi tạo thủ công: tên = từ cuối.
        assertEquals("An", jdbc.queryForObject("select first_name from leader where email='an@example.com'", String.class));
        assertEquals("Nguyễn Văn", jdbc.queryForObject("select last_name from leader where email='an@example.com'", String.class));
    }

    @Test
    @Order(2)
    void leaderImportRejectsWholeFileAndSavesNothingOnAnyBadRow() {
        MockMultipartFile file = excel(LEADER_HEADERS, List.of(
                new Row("Phêrô", "Hợp Lệ Một", "01/01/1990", "Nam", "ok1@example.com", "0911111111", "", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Maria", "Trùng Email", "01/01/1990", "Nữ", "AN@example.com", "0911111112", "", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Anna", "Trùng SĐT", "01/01/1990", "Nữ", "ok3@example.com", "0901234567", "", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Giuse", "Sai Cấp", "01/01/1990", "Nam", "ok4@example.com", "0911111113", "Cấp không có", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Luca", "Sai Giáo Xứ", "01/01/1990", "Nam", "ok5@example.com", "0911111114", "", "Thủ Dầu Một", "Không tồn tại"),
                new Row("Marco", "Ngoài Phạm Vi", "01/01/1990", "Nam", "ok6@example.com", "0911111115", "", "Long Khánh", "Gia Kiệm"),
                new Row("", "Thiếu Tên Thánh", "99/99/9999", "Khác", "khong-phai-email", "123", "", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Pio", "Giáo Hạt Lạ", "01/01/1990", "Nam", "ok7@example.com", "0911111116", "", "Giáo hạt lạ", "Bà Lụa"),
                new Row("Teresa", "Thiếu SĐT Và Giáo Hạt", "01/01/1990", "Nữ", "ok8@example.com", "", "", "", "Bà Lụa"),
                new Row("Clara", "Sai Giáo Hạt", "01/01/1990", "Nữ", "ok9@example.com", "0911111117", "", "Long Khánh", "Bà Lụa")));

        ImportValidationException exception = assertThrows(ImportValidationException.class,
                () -> leaderImportService.importLeaders(path(file), phuCuongAdmin(), JobProgress.NONE));

        var messages = exception.getErrors().stream().map(error -> error.row() + ":" + error.column() + ":" + error.message()).toList();
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("3:Email:") && m.contains("đã tồn tại")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("4:Số điện thoại:") && m.contains("đã tồn tại")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("5:Cấp độ:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("6:Giáo xứ:") && m.contains("chưa có trong hệ thống")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("7:Giáo xứ:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("9:Giáo hạt:") && m.contains("chưa có trong hệ thống")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("10:Số điện thoại:") && m.contains("Bắt buộc")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("10:Giáo hạt:") && m.contains("Bắt buộc")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("11:Giáo xứ:") && m.contains("không thuộc giáo hạt")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("8:Tên thánh:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("8:Ngày sinh:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("8:Giới tính:")), messages.toString());
        // Dòng hợp lệ (dòng 2) cũng không được lưu.
        assertEquals(3, jdbc.queryForObject("select count(*) from leader", Integer.class));
    }

    @Test
    @Order(3)
    void phoneNumberIsNowUniqueWhenCreatingOrUpdatingByHand() {
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setHolyName("Phaolô");
        request.setFullName("Phạm Văn Dũng");
        request.setBirthDate(LocalDate.of(1992, 5, 5));
        request.setEmail("dung@example.com");
        request.setPhoneNumber("0901234567");
        request.setGender(LeaderGender.NAM);
        request.setLeaderLevel("NONE");
        request.setParishId(jdbc.queryForObject("select id from parish where name='Bà Lụa'", UUID.class));
        ApiException exception = assertThrows(ApiException.class, () -> leaderService.create(request, phuCuongAdmin()));
        assertTrue(exception.getMessage().contains("Số điện thoại"), exception.getMessage());
        assertThrows(Exception.class, () -> jdbc.update("update leader set phone_number='0901234567' where email='binh@example.com'"));
    }

    @Test
    @Order(4)
    void createPastCoursesWithoutCourseTypeAndRegistrationWindow() {
        PastTrainingCourseRequest request = new PastTrainingCourseRequest();
        request.setCourseName("Khóa Huynh trưởng cấp I 2019");
        request.setHostYear(2019);
        request.setResultingLevel("HT_I");
        TrainingCourseResponse created = pastTrainingCourseService.create(request, phuCuongAdmin());
        courseCode = created.getCourseCode();

        assertTrue(created.getPastCourse());
        assertEquals("HT_I", created.getResultingLevel());
        assertEquals("Huynh trưởng cấp I", created.getResultingLevelName());
        assertTrue(created.getCertificateRequired());
        assertEquals(2019, created.getHostYear());
        assertEquals(null, created.getCourseTypeCode());
        assertEquals(null, created.getRegistrationStartAt());
        assertEquals("ENDED", created.getOrganizingStatus().name());
        assertEquals("ENDED", created.getRegistrationStatus().name());
        assertNotNull(created.getFinishedAt());
        assertEquals(dioceseId, created.getDioceseId());

        PastTrainingCourseRequest second = new PastTrainingCourseRequest();
        second.setCourseName("Khóa Dự trưởng 2020");
        second.setHostYear(2020);
        second.setStartDate(LocalDate.of(2020, 7, 1));
        second.setEndDate(LocalDate.of(2020, 7, 5));
        second.setResultingLevel("DU_TRUONG");
        secondCourseCode = pastTrainingCourseService.create(second, phuCuongAdmin()).getCourseCode();

        // Khóa tương lai bị từ chối; năm tổ chức phải ≤ năm hiện tại.
        PastTrainingCourseRequest future = new PastTrainingCourseRequest();
        future.setCourseName("Khóa tương lai");
        future.setHostYear(LocalDate.now().getYear() + 1);
        assertThrows(ApiException.class, () -> pastTrainingCourseService.create(future, phuCuongAdmin()));
        // Cấp độ không có trong danh mục của giáo phận bị từ chối.
        PastTrainingCourseRequest badLevel = new PastTrainingCourseRequest();
        badLevel.setCourseName("Sai cấp độ");
        badLevel.setHostYear(2018);
        badLevel.setResultingLevel("KHONG_CO");
        assertThrows(ApiException.class, () -> pastTrainingCourseService.create(badLevel, phuCuongAdmin()));
    }

    @Test
    @Order(5)
    void adminOfAnotherDioceseCannotTouchThePastCourse() {
        Authentication xuanLoc = admin(OTHER_USERNAME, "training.course.create_past.diocese");
        MockMultipartFile file = excel(List.of("Email", "Kết quả *"), List.of(new Row("an@example.com", "Đạt")));
        assertThrows(ApiException.class, () -> participantImportService.importParticipants(courseCode, path(file), xuanLoc, JobProgress.NONE));
        PastTrainingCourseRequest request = new PastTrainingCourseRequest();
        request.setCourseName("Đổi tên trái phép");
        request.setHostYear(2019);
        assertThrows(ApiException.class, () -> pastTrainingCourseService.update(courseCode, request, xuanLoc));
    }

    @Test
    @Order(6)
    void importParticipantsFindsLeadersByEmailOrPhoneAndRejectsBadRows() {
        // Lỗi: không tìm thấy, hai thông tin thuộc hai người khác nhau, trùng trong file, kết quả sai.
        MockMultipartFile bad = excel(PARTICIPANT_HEADERS, List.of(
                fullRow("an@example.com", "Đạt", "8.5"),
                participant("khong.co@example.com", "0977000001", "Đạt", "", "", "", "", "", "", ""),
                fullRow("an@example.com", "0901234569", "Đạt", "7", ""),
                fullRow("binh@example.com", "Có lẽ", "7"),
                participant("thieu.sdt@example.com", "", "Đạt", "Phêrô", "Thiếu SĐT", "01/01/1990", "Nam", "", "Thủ Dầu Một", "Búng"),
                fullRow("an@example.com", "Đạt", "9")));
        ImportValidationException exception = assertThrows(ImportValidationException.class,
                () -> participantImportService.importParticipants(courseCode, path(bad), phuCuongAdmin(), JobProgress.NONE));
        var messages = exception.getErrors().stream().map(error -> error.row() + ":" + error.column() + ":" + error.message()).toList();
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("3:Tên thánh:") && m.contains("Bắt buộc")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("4:Email / Số điện thoại:") && m.contains("hai huynh trưởng khác nhau")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("5:Kết quả:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("6:Số điện thoại:") && m.contains("Bắt buộc")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("7:Email / Số điện thoại:") && m.contains("nhiều lần")), messages.toString());
        assertEquals(0, jdbc.queryForObject("select count(*) from course_participation", Integer.class));

        // Hợp lệ: An theo email (Đạt), Bình theo SĐT? (Bình không có SĐT -> theo email), Cường theo SĐT (Không đạt).
        MockMultipartFile good = excel(PARTICIPANT_HEADERS, List.of(
                fullRow("an@example.com", null, "Đạt", "8.5", "Tốt"),
                fullRow("binh@example.com", "Đạt", "7"),
                fullRow("cuong@example.com", "Không đạt", "4")));
        ImportSummary summary = participantImportService.importParticipants(courseCode, path(good), phuCuongAdmin(), JobProgress.NONE);
        assertEquals(3, summary.created());

        assertEquals(3, jdbc.queryForObject("select count(*) from course_participation where course_id=?", Integer.class, courseCode));
        // Học viên Đạt của khóa có cấp độ: chờ duyệt chứng nhận (có chứng nhận đề xuất mã QK).
        assertEquals(2, jdbc.queryForObject("select count(*) from certificate where course_code=? and approval_status='PENDING'", Integer.class, courseCode));
        List<String> codes = jdbc.queryForList("select certificate_code from certificate where course_code=? order by certificate_code", String.class, courseCode);
        assertTrue(codes.stream().allMatch(code -> code.matches("GPPC-QK-2019-\\d+")), codes.toString());
        // Không đạt: kết quả chung là không đạt ngay, không có chứng nhận.
        assertEquals("FAILED", jdbc.queryForObject(
                "select final_result from course_participation cp join leader l on l.id=cp.leader_id where l.email='cuong@example.com' and cp.course_id=?", String.class, courseCode));

        // Import lần hai cùng huynh trưởng bị từ chối (đã có trong danh sách).
        assertThrows(ImportValidationException.class, () -> participantImportService.importParticipants(courseCode, path(good), phuCuongAdmin(), JobProgress.NONE));
        assertEquals(3, jdbc.queryForObject("select count(*) from course_participation where course_id=?", Integer.class, courseCode));
    }

    private String certificateCodeOf(String email, String course) {
        return jdbc.queryForObject("select c.certificate_code from certificate c join leader l on l.id=c.leader_id where l.email=? and c.course_code=?", String.class, email, course);
    }

    private static CertificateApproveRequest approval(LocalDate date) {
        CertificateApproveRequest request = new CertificateApproveRequest();
        request.setSignerType(CertificateSignerType.CHAPLAIN);
        request.setSignerName("Cha Giuse");
        request.setApprovalDate(date);
        return request;
    }

    @Test
    @Order(7)
    void approvingPastCertificateNeedsAChosenDateAndWritesRankHistory() {
        String code = certificateCodeOf("an@example.com", courseCode);

        // Không có ngày duyệt hoặc ngày ở tương lai: bị từ chối.
        assertThrows(ApiException.class, () -> certificateService.approve(code, approval(null), phuCuongAdmin()));
        assertThrows(ApiException.class, () -> certificateService.approve(code, approval(LocalDate.now().plusDays(1)), phuCuongAdmin()));
        // Trước năm tổ chức khóa (2019) cũng bị từ chối.
        assertThrows(ApiException.class, () -> certificateService.approve(code, approval(LocalDate.of(2018, 12, 31)), phuCuongAdmin()));
        assertEquals("PENDING", jdbc.queryForObject("select approval_status from certificate where certificate_code=?", String.class, code));

        LocalDate chosen = LocalDate.of(2019, 12, 20);
        var response = certificateService.approve(code, approval(chosen), phuCuongAdmin());
        assertEquals("APPROVED", response.getApprovalStatus().name());
        // Ngày cấp / ngày duyệt là ngày đã chọn, không phải hôm nay.
        assertEquals(chosen, response.getIssueDate());
        assertEquals(chosen, jdbc.queryForObject("select issue_date from certificate where certificate_code=?", LocalDate.class, code));
        assertEquals(chosen, jdbc.queryForObject("select (approved_at at time zone 'Asia/Ho_Chi_Minh')::date from certificate where certificate_code=?", LocalDate.class, code));

        // Lịch sử cấp độ: HT_XU -> HT_I theo ngày duyệt đã chọn; cấp hiện tại tăng lên HT_I.
        assertEquals("HT_I", jdbc.queryForObject("select leader_level from leader where email='an@example.com'", String.class));
        var history = jdbc.queryForList("select old_level, new_level, promotion_date, course_code, reverted from rank_history rh join leader l on l.id=rh.leader_id where l.email='an@example.com'");
        assertEquals(1, history.size());
        assertEquals("HT_XU", history.get(0).get("old_level"));
        assertEquals("HT_I", history.get(0).get("new_level"));
        assertEquals(chosen, ((java.sql.Date) history.get(0).get("promotion_date")).toLocalDate());
        assertEquals(courseCode, history.get(0).get("course_code"));
        assertEquals("PASSED", jdbc.queryForObject("select final_result from course_participation cp join leader l on l.id=cp.leader_id where l.email='an@example.com' and cp.course_id=?", String.class, courseCode));
    }

    @Test
    @Order(8)
    void lowerLevelPastCourseAddsHistoryButNeverLowersCurrentLevel() {
        // Cường (Dự trưởng, Không đạt khóa 1) và An (đã HT_I) cùng học khóa Dự trưởng 2020 sau đó.
        MockMultipartFile file = excel(PARTICIPANT_HEADERS, List.of(
                fullRow("an@example.com", "Đạt", "7"),
                fullRow("cuong@example.com", "Đạt", "7")));
        assertEquals(2, participantImportService.importParticipants(secondCourseCode, path(file), phuCuongAdmin(), JobProgress.NONE).created());

        String code = certificateCodeOf("an@example.com", secondCourseCode);
        assertTrue(code.matches("GPPC-QK-2020-\\d+"), code);
        certificateService.approve(code, approval(LocalDate.of(2020, 8, 10)), phuCuongAdmin());

        // An đã HT_I (cao hơn Dự trưởng): thêm một dòng lịch sử nhưng giữ nguyên cấp hiện tại.
        assertEquals("HT_I", jdbc.queryForObject("select leader_level from leader where email='an@example.com'", String.class));
        var rows = jdbc.queryForList("select new_level, old_level from rank_history rh join leader l on l.id=rh.leader_id where l.email='an@example.com' order by rh.id");
        assertEquals(2, rows.size());
        assertEquals("DU_TRUONG", rows.get(1).get("new_level"));
        assertEquals("HT_I", rows.get(1).get("old_level"));

        // Cường đang ở Dự trưởng: duyệt khóa Dự trưởng không đổi cấp nhưng vẫn ghi lịch sử.
        certificateService.approve(certificateCodeOf("cuong@example.com", secondCourseCode), approval(LocalDate.of(2021, 1, 15)), phuCuongAdmin());
        assertEquals("DU_TRUONG", jdbc.queryForObject("select leader_level from leader where email='cuong@example.com'", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from rank_history rh join leader l on l.id=rh.leader_id where l.email='cuong@example.com'", Integer.class));
    }

    @Test
    @Order(9)
    void exportFollowsTheFilterAndWholeListWhenUnfiltered() throws Exception {
        // Không lọc: xuất tất cả huynh trưởng trong phạm vi.
        ExcelSheetData all = readExport(java.util.Map.of());
        assertEquals(3, all.rows().size());
        assertTrue(all.hasColumn("Cấp độ"));
        assertEquals("Huynh trưởng cấp I", all.rows().stream().filter(r -> "an@example.com".equals(r.text("Email"))).findFirst().orElseThrow().text("Cấp độ"));

        // Có lọc: chỉ xuất theo bộ lọc (giáo xứ Búng chỉ có 1 người) và theo từ khóa tìm kiếm.
        String filters = "{\"parish.name\":\"Búng\"}";
        ExcelSheetData filtered = readExport(java.util.Map.of("filters", filters));
        assertEquals(1, filtered.rows().size());
        assertEquals("binh@example.com", filtered.rows().get(0).text("Email"));

        ExcelSheetData searched = readExport(java.util.Map.of("search", "Cường", "searchFields", "firstName"));
        assertEquals(1, searched.rows().size());
        assertEquals("cuong@example.com", searched.rows().get(0).text("Email"));
    }

    // Xuất là tác vụ nền: POST tạo job (202), chờ job xong rồi đọc file kết quả.
    private ExcelSheetData readExport(java.util.Map<String, String> params) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/leaders/export")
                .header("Idempotency-Key", newKey());
        params.forEach(request::queryParam);
        MvcResult result = mockMvc.perform(request.with(authentication(phuCuongAdmin()))).andReturn();
        assertEquals(202, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        UUID jobId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).path("data").path("id").asText());
        var job = awaitJob(jobId);
        assertEquals("SUCCEEDED", job.status(), job.errorMessage());
        var download = excelJobService.download(jobId, phuCuongAdmin());
        assertTrue(download.fileName().endsWith(".xlsx"));
        MockMultipartFile upload = new MockMultipartFile("file", "export.xlsx", ExcelResponses.XLSX.toString(), java.nio.file.Files.readAllBytes(download.path()));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload)) {
            return reader.firstSheet();
        }
    }

    @Test
    @Order(10)
    void exportCollectsMoreRowsThanOnePage() {
        // 130 huynh trưởng > 100/trang: xuất phải gom đủ các trang.
        UUID parishId = jdbc.queryForObject("select id from parish where name='Búng'", UUID.class);
        for (int i = 0; i < 130; i++) {
            jdbc.update("insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, gender, leader_level, parish_id) "
                    + "values (gen_random_uuid(), now(), true, 'Gioan', 'Bulk' || ?, 'Test', '1990-01-01', ? , 'NAM', 'NONE', ?)", i, "bulk" + i + "@example.com", parishId);
        }
        // Dữ liệu chèn thẳng bằng SQL không đi qua service nên không tự xóa cache danh sách.
        cacheManager.getCache(phucuong.ht.config.CacheNames.LEADER_LIST).clear();
        List<phucuong.ht.modules.leader.model.response.LeaderResponse> rows = phucuong.ht.common.excel.ExcelExportSupport.collectAll(
                new BaseSearchRequest(), page -> leaderService.findAll(page, phuCuongAdmin()));
        assertEquals(133, rows.size());
        assertEquals(133, rows.stream().map(r -> r.getEmail()).distinct().count());
        assertFalse(rows.isEmpty());
    }

    @Test
    @Order(11)
    void wrongImportCanBeUndoneUnlessTheCertificateIsAlreadyApproved() {
        String participationOfAn = jdbc.queryForObject(
                "select cp.id from course_participation cp join leader l on l.id=cp.leader_id where l.email='an@example.com' and cp.course_id=?", String.class, courseCode);
        String participationOfBinh = jdbc.queryForObject(
                "select cp.id from course_participation cp join leader l on l.id=cp.leader_id where l.email='binh@example.com' and cp.course_id=?", String.class, courseCode);

        // An đã được duyệt chứng nhận: không xóa được, phải thu hồi trước.
        assertThrows(ApiException.class, () -> participantImportService.removeParticipant(courseCode, participationOfAn, phuCuongAdmin()));
        // Admin giáo phận khác không xóa được.
        assertThrows(ApiException.class, () -> participantImportService.removeParticipant(courseCode, participationOfBinh,
                admin(OTHER_USERNAME, "training.course.create_past.diocese")));
        // Mã hồ sơ của khóa khác không dùng được.
        assertThrows(ApiException.class, () -> participantImportService.removeParticipant(secondCourseCode, participationOfBinh, phuCuongAdmin()));

        // Bình (chứng nhận chờ duyệt) bị xóa cùng chứng nhận đề xuất, rồi import lại được.
        participantImportService.removeParticipant(courseCode, participationOfBinh, phuCuongAdmin());
        assertEquals(0, jdbc.queryForObject("select count(*) from course_participation where id=?", Integer.class, participationOfBinh));
        assertEquals(0, jdbc.queryForObject("select count(*) from certificate c join leader l on l.id=c.leader_id where l.email='binh@example.com' and c.course_code=?", Integer.class, courseCode));
        MockMultipartFile again = excel(PARTICIPANT_HEADERS, List.of(fullRow("binh@example.com", "Không đạt", "3")));
        assertEquals(1, participantImportService.importParticipants(courseCode, path(again), phuCuongAdmin(), JobProgress.NONE).created());
    }

    // ================= Tác vụ nền, idempotency và race condition =================

    @Autowired private phucuong.ht.common.excel.job.ExcelJobService excelJobService;
    @Autowired private phucuong.ht.common.excel.job.ExcelJobRepository excelJobRepository;
    @Autowired private phucuong.ht.modules.certificate.service.CertificateIdempotentActions certificateActions;

    private static MockMultipartFile importedFile;
    private static String course3;
    private static String course4;
    private static String course5;
    private static String course6;

    private static String newKey() {
        return "test-" + UUID.randomUUID();
    }

    private phucuong.ht.common.excel.job.ExcelJobResponse awaitJob(UUID id) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            var job = excelJobService.get(id, phuCuongAdmin());
            if (!"QUEUED".equals(job.status()) && !"RUNNING".equals(job.status())
                    && !"PENDING".equals(job.avatarStatus()) && !"RUNNING".equals(job.avatarStatus())) {
                return job;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new AssertionError("Tác vụ không kết thúc trong 60 giây: " + id);
    }

    private int countLeaders(String emailPattern) {
        return jdbc.queryForObject("select count(*) from leader where email like ?", Integer.class, emailPattern);
    }

    @Test
    @Order(12)
    void backgroundImportRunsOnceAndTheSameKeyReturnsTheSameJob() {
        importedFile = excel(LEADER_HEADERS, List.of(
                new Row("Anna", "Hoàng Thị Giang", "03/03/1993", "Nữ", "giang@example.com", "0933333331", "", "Thủ Dầu Một", "Bà Lụa"),
                new Row("Luca", "Đỗ Văn Hải", "04/04/1994", "Nam", "hai@example.com", "0933333332", "", "Thủ Dầu Một", "Búng")));
        String key = newKey();
        var first = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", importedFile, key, phuCuongAdmin());
        assertFalse(first.replayed());
        // Worker có thể nhận việc ngay trước khi response được trả về nên chấp nhận cả RUNNING.
        assertTrue(List.of("QUEUED", "RUNNING").contains(first.status()), first.status());

        var done = awaitJob(first.id());
        assertEquals("SUCCEEDED", done.status(), done.errorMessage());
        assertEquals(2, done.created());
        assertEquals(2, countLeaders("giang@example.com") + countLeaders("hai@example.com"));

        // Gửi lại cùng key (bấm đúp / retry mạng): nhận lại đúng job cũ, không import lần hai.
        var again = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", importedFile, key, phuCuongAdmin());
        assertTrue(again.replayed());
        assertEquals(first.id(), again.id());
        assertEquals(1, jdbc.queryForObject("select count(*) from excel_job where idempotency_key=?", Integer.class, key));

        // Cùng key nhưng nội dung file khác: từ chối.
        MockMultipartFile other = excel(LEADER_HEADERS, List.of(
                new Row("Anna", "Người Khác", "03/03/1993", "Nữ", "khac@example.com", "0933333350", "", "Thủ Dầu Một", "Bà Lụa")));
        assertThrows(ApiException.class, () -> excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", other, key, phuCuongAdmin()));
        assertEquals(0, countLeaders("khac@example.com"));

        // Thiếu Idempotency-Key: từ chối.
        assertThrows(ApiException.class, () -> excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", other, null, phuCuongAdmin()));
    }

    @Test
    @Order(13)
    void importingTheSameFileAgainOnlyWarnsAndTheJobReportsTheDuplicateRows() {
        int before = jdbc.queryForObject("select count(*) from leader", Integer.class);
        var job = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", importedFile, newKey(), phuCuongAdmin());
        assertNotNull(job.warning());
        assertTrue(job.warning().contains("đã được import"), job.warning());

        var done = awaitJob(job.id());
        assertEquals("FAILED", done.status());
        assertNotNull(done.errors());
        assertTrue(done.errors().stream().anyMatch(error -> error.column().equals("Email") && error.message().contains("đã tồn tại")), done.errors().toString());
        // Không lưu gì và file tạm đã được dọn.
        assertEquals(before, jdbc.queryForObject("select count(*) from leader", Integer.class));
    }

    @Test
    @Order(14)
    void manyConcurrentRequestsWithTheSameKeyCreateExactlyOneJobAndOneImport() throws Exception {
        MockMultipartFile file = excel(LEADER_HEADERS, List.of(
                new Row("Marta", "Vũ Thị Lan", "05/05/1995", "Nữ", "lan.concurrent@example.com", "0933333340", "", "Thủ Dầu Một", "Bà Lụa")));
        String key = newKey();
        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<phucuong.ht.common.excel.job.ExcelJobResponse>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", file, key, phuCuongAdmin());
                }));
            }
            start.countDown();
            List<phucuong.ht.common.excel.job.ExcelJobResponse> results = new java.util.ArrayList<>();
            for (var future : futures) {
                results.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(1, results.stream().map(phucuong.ht.common.excel.job.ExcelJobResponse::id).distinct().count());
            assertEquals(1, results.stream().filter(job -> !job.replayed()).count(), "đúng một request tạo job mới");
            assertEquals("SUCCEEDED", awaitJob(results.get(0).id()).status());
            assertEquals(1, countLeaders("lan.concurrent@example.com"));
            assertEquals(1, jdbc.queryForObject("select count(*) from excel_job where idempotency_key=?", Integer.class, key));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(15)
    void onlyOneActiveJobPerKindAndTargetAndRunningImportsCannotBeCancelled() {
        UUID active = UUID.randomUUID();
        jdbc.update("insert into excel_job (id, owner, kind, target, idempotency_key, payload_hash, status, principal_json, lease_until) "
                + "values (?, ?, 'IMPORT_PARTICIPANTS', 'COURSE-X', ?, 'hash', 'RUNNING', '{}', now() + interval '1 hour')", active, USERNAME, newKey());
        MockMultipartFile file = excel(List.of("Email", "Kết quả *"), List.of(new Row("an@example.com", "Đạt")));
        ApiException conflict = assertThrows(ApiException.class, () -> excelJobService.createImport(
                phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_PARTICIPANTS, "COURSE-X", file, newKey(), phuCuongAdmin()));
        assertTrue(conflict.getMessage().contains("đang chạy"), conflict.getMessage());
        // Đích khác thì không bị chặn (không có job nào chạy cho đích đó).
        assertEquals(0, jdbc.queryForObject("select count(*) from excel_job where target='COURSE-Y'", Integer.class));

        assertThrows(ApiException.class, () -> excelJobService.cancel(active, phuCuongAdmin()));
        // Người khác không thấy được tác vụ của mình.
        assertThrows(ApiException.class, () -> excelJobService.get(active, admin(OTHER_USERNAME)));

        // Job RUNNING hết lease và hết lượt chạy lại thì báo lỗi.
        jdbc.update("update excel_job set lease_until = now() - interval '1 minute', attempts = 3 where id = ?", active);
        excelJobRepository.recoverStale(3);
        assertEquals("FAILED", jdbc.queryForObject("select status from excel_job where id=?", String.class, active));
    }

    @Test
    @Order(16)
    void exportJobWritesTheFilteredFileInTheBackgroundAndIsIdempotent() throws Exception {
        BaseSearchRequest request = new BaseSearchRequest();
        request.getFilters().put("parish.name", "Búng");
        String key = newKey();
        var job = excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_LEADERS, request, key, phuCuongAdmin());
        var done = awaitJob(job.id());
        assertEquals("SUCCEEDED", done.status(), done.errorMessage());
        assertTrue(done.downloadable());
        assertTrue(done.fileName().startsWith("ho-so-huynh-truong-"));

        var file = excelJobService.download(job.id(), phuCuongAdmin());
        MockMultipartFile upload = new MockMultipartFile("file", "export.xlsx", ExcelResponses.XLSX.toString(), java.nio.file.Files.readAllBytes(file.path()));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload)) {
            int expected = jdbc.queryForObject("select count(*) from leader l join parish p on p.id=l.parish_id where p.name='Búng'", Integer.class);
            assertEquals(expected, reader.firstSheet().rows().size());
        }
        // Cùng key + cùng bộ lọc: trả lại đúng job; cùng key khác bộ lọc: từ chối.
        assertEquals(job.id(), excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_LEADERS, request, key, phuCuongAdmin()).id());
        BaseSearchRequest other = new BaseSearchRequest();
        other.getFilters().put("parish.name", "Bà Lụa");
        assertThrows(ApiException.class, () -> excelJobService.createExport(phucuong.ht.common.excel.job.ExcelJobKind.EXPORT_LEADERS, other, key, phuCuongAdmin()));
        // Người khác không tải được file của mình.
        assertThrows(ApiException.class, () -> excelJobService.download(job.id(), admin(OTHER_USERNAME)));
    }

    @Test
    @Order(17)
    void leaderEmailIsUniqueIgnoringCase() {
        UUID parishId = jdbc.queryForObject("select id from parish where name='Bà Lụa'", UUID.class);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, gender, leader_level, parish_id) "
                        + "values (gen_random_uuid(), now(), true, 'Gioan', 'Upper', 'Case', '1990-01-01', 'AN@EXAMPLE.COM', 'NAM', 'NONE', ?)", parishId));
    }

    private String createPastCourse(String name, int year, String level) {
        PastTrainingCourseRequest request = new PastTrainingCourseRequest();
        request.setCourseName(name);
        request.setHostYear(year);
        request.setResultingLevel(level);
        return pastTrainingCourseService.create(request, phuCuongAdmin()).getCourseCode();
    }

    private void addParticipants(String course, String... emails) {
        List<Row> rows = java.util.Arrays.stream(emails).map(email -> fullRow(email, "Đạt", "7")).toList();
        participantImportService.importParticipants(course, path(excel(PARTICIPANT_HEADERS, rows)), phuCuongAdmin(), JobProgress.NONE);
    }

    private static CertificateApproveRequest approvalOn(String date) {
        return approval(LocalDate.parse(date));
    }

    private int historyCount(String email) {
        return jdbc.queryForObject("select count(*) from rank_history rh join leader l on l.id=rh.leader_id where l.email=?", Integer.class, email);
    }

    private String levelOf(String email) {
        return jdbc.queryForObject("select leader_level from leader where email=?", String.class, email);
    }

    @Test
    @Order(18)
    void approvingWithTheSameIdempotencyKeyTwiceApprovesOnlyOnce() {
        course3 = createPastCourse("Khóa HT cấp II 2021", 2021, "HT_II");
        addParticipants(course3, "cuong@example.com", "binh@example.com", "an@example.com");
        String code = certificateCodeOf("cuong@example.com", course3);
        String key = newKey();
        int historyBefore = historyCount("cuong@example.com");

        var first = certificateActions.approve(code, approvalOn("2021-06-01"), key, phuCuongAdmin());
        assertFalse(first.replayed());
        // Bấm đúp / retry cùng key: nhận lại kết quả cũ, không duyệt thêm lần nữa, không ghi thêm lịch sử cấp độ.
        var second = certificateActions.approve(code, approvalOn("2021-06-01"), key, phuCuongAdmin());
        assertTrue(second.replayed());
        assertEquals(historyBefore + 1, historyCount("cuong@example.com"));
        assertEquals("HT_II", levelOf("cuong@example.com"));

        // Cùng key nhưng nội dung khác (ngày duyệt khác): từ chối, dữ liệu không đổi.
        assertThrows(ApiException.class, () -> certificateActions.approve(code, approvalOn("2021-07-01"), key, phuCuongAdmin()));
        assertEquals(LocalDate.of(2021, 6, 1), jdbc.queryForObject("select issue_date from certificate where certificate_code=?", LocalDate.class, code));
        // Key mới trên chứng nhận đã duyệt: bị từ chối vì đã duyệt (không ghi lịch sử lần hai).
        assertThrows(ApiException.class, () -> certificateActions.approve(code, approvalOn("2021-06-01"), newKey(), phuCuongAdmin()));
        assertEquals(historyBefore + 1, historyCount("cuong@example.com"));
    }

    @Test
    @Order(19)
    void failedActionDoesNotConsumeTheIdempotencyKey() {
        String code = certificateCodeOf("binh@example.com", course3);
        String key = newKey();
        // Thiếu ngày duyệt -> lỗi, transaction rollback nên key chưa bị giữ.
        assertThrows(ApiException.class, () -> certificateActions.approve(code, approval(null), key, phuCuongAdmin()));
        assertEquals(0, jdbc.queryForObject("select count(*) from idempotency_record where idempotency_key=?", Integer.class, key));
        // Gửi lại cùng key với dữ liệu đúng thì chạy bình thường.
        // (Cùng key khác nội dung hợp lệ vì lần trước chưa được ghi nhận.)
        var ok = certificateActions.approve(code, approvalOn("2021-08-01"), key, phuCuongAdmin());
        assertFalse(ok.replayed());
        assertEquals("APPROVED", jdbc.queryForObject("select approval_status from certificate where certificate_code=?", String.class, code));
    }

    private <T> List<Object> runConcurrently(int threads, java.util.function.IntFunction<java.util.concurrent.Callable<T>> task) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Object>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                var callable = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return callable.call();
                    } catch (Exception exception) {
                        return exception;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new java.util.ArrayList<>();
            for (var future : futures) {
                results.add(future.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(20)
    void manyAdminsApprovingTheSameCertificateAtOnceOnlyOneSucceeds() throws Exception {
        course4 = createPastCourse("Khóa HT cấp III 2022", 2022, "HT_III");
        addParticipants(course4, "an@example.com", "cuong@example.com");
        String code = certificateCodeOf("an@example.com", course4);
        int historyBefore = historyCount("an@example.com");

        List<Object> results = runConcurrently(8, i -> () -> certificateActions.approve(code, approvalOn("2022-05-0" + (1 + i % 9)), newKey(), phuCuongAdmin()));

        assertEquals(1, results.stream().filter(r -> r instanceof phucuong.ht.common.idempotency.IdempotencyService.Outcome).count(), results.toString());
        assertEquals(7, results.stream().filter(r -> r instanceof ApiException).count(), results.toString());
        assertEquals(historyBefore + 1, historyCount("an@example.com"), "chỉ một dòng lịch sử cấp độ");
        assertEquals("HT_III", levelOf("an@example.com"));
        assertEquals("APPROVED", jdbc.queryForObject("select approval_status from certificate where certificate_code=?", String.class, code));
    }

    @Test
    @Order(21)
    void concurrentRequestsWithTheSameKeyAllSucceedButApproveOnce() throws Exception {
        String code = certificateCodeOf("cuong@example.com", course4);
        String key = newKey();
        int historyBefore = historyCount("cuong@example.com");
        var request = approvalOn("2022-09-09");

        List<Object> results = runConcurrently(8, i -> () -> certificateActions.approve(code, request, key, phuCuongAdmin()));

        assertTrue(results.stream().allMatch(r -> r instanceof phucuong.ht.common.idempotency.IdempotencyService.Outcome), results.toString());
        assertEquals(1, results.stream().map(r -> (phucuong.ht.common.idempotency.IdempotencyService.Outcome) r).filter(o -> !o.replayed()).count());
        assertEquals(historyBefore + 1, historyCount("cuong@example.com"));
        assertEquals("HT_III", levelOf("cuong@example.com"));
    }

    @Test
    @Order(22)
    void approvingTwoCertificatesOfTheSameLeaderConcurrentlyKeepsTheHighestLevelAndBothHistoryRows() throws Exception {
        // Huynh trưởng Bình đang HT_II; hai khóa quá khứ mới cùng được duyệt một lúc: HLV_I (cao nhất) và HT_III.
        course5 = createPastCourse("Khóa HT cấp III 2023", 2023, "HT_III");
        course6 = createPastCourse("Khóa HLV I 2024", 2024, "HLV_I");
        addParticipants(course5, "binh@example.com");
        addParticipants(course6, "binh@example.com");
        String codeA = certificateCodeOf("binh@example.com", course5);
        String codeB = certificateCodeOf("binh@example.com", course6);
        int historyBefore = historyCount("binh@example.com");

        List<Object> results = runConcurrently(2, i -> () -> i == 0
                ? certificateActions.approve(codeA, approvalOn("2023-04-01"), newKey(), phuCuongAdmin())
                : certificateActions.approve(codeB, approvalOn("2024-04-01"), newKey(), phuCuongAdmin()));

        assertTrue(results.stream().allMatch(r -> r instanceof phucuong.ht.common.idempotency.IdempotencyService.Outcome), results.toString());
        assertEquals(historyBefore + 2, historyCount("binh@example.com"));
        assertEquals("HLV_I", levelOf("binh@example.com"), "cấp cao nhất thắng, không mất cập nhật");
    }

    @Test
    @Order(23)
    void concurrentRevokesAndTheIdempotentReissueKeepTheLevelsConsistent() throws Exception {
        String code = certificateCodeOf("an@example.com", course4);
        assertEquals("HT_III", levelOf("an@example.com"));

        CertificateRevokeRequestHolder holder = new CertificateRevokeRequestHolder();
        List<Object> results = runConcurrently(6, i -> () -> certificateActions.revoke(code, holder.request("Thu hồi thử " + i), newKey(), phuCuongAdmin()));
        assertEquals(1, results.stream().filter(r -> r instanceof phucuong.ht.common.idempotency.IdempotencyService.Outcome).count(), results.toString());
        assertEquals("REVOKED", jdbc.queryForObject("select approval_status from certificate where certificate_code=?", String.class, code));
        // Hạ về cấp trước đó (HT_II đã đạt ở khóa HT cấp II, hoặc HT_I) đúng một lần, không hạ hai lần.
        assertTrue(List.of("HT_II", "HT_I").contains(levelOf("an@example.com")), levelOf("an@example.com"));

        // Cấp lại với cùng key hai lần: chỉ cấp lại một lần.
        var reissue = new phucuong.ht.modules.certificate.model.request.CertificateReissueRequest();
        reissue.setReason("Cấp lại do nhầm lẫn");
        String key = newKey();
        assertFalse(certificateActions.reissue(code, reissue, key, phuCuongAdmin()).replayed());
        assertTrue(certificateActions.reissue(code, reissue, key, phuCuongAdmin()).replayed());
        assertEquals("APPROVED", jdbc.queryForObject("select approval_status from certificate where certificate_code=?", String.class, code));
        assertEquals("HT_III", levelOf("an@example.com"));
    }

    private static final class CertificateRevokeRequestHolder {
        phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest request(String reason) {
            var request = new phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest();
            request.setReason(reason);
            return request;
        }
    }

    // ================= Tạo huynh trưởng ngay trong file danh sách tham dự; admin giáo hạt =================

    private static final List<String> PARTICIPANT_HEADERS = List.of("Email *", "Số điện thoại *", "Tổng điểm *", "Kết quả *", "Nhận xét",
            "Tên thánh *", "Họ và tên *", "Ngày sinh *", "Giới tính *", "Cấp độ", "Giáo hạt *", "Giáo xứ *");

    private static Row participant(String email, String phone, String result, String holy, String name, String birth, String gender,
                                   String level, String deanery, String parish) {
        return new Row(email, phone, "7", result, "", holy, name, birth, gender, level, deanery, parish);
    }

    // Dòng danh sách tham dự đủ cột cho một huynh trưởng đã có hồ sơ (thông tin lấy từ chính hồ sơ trong DB).
    private Row fullRow(String email, String phoneOverride, String result, String score, String review) {
        var m = jdbc.queryForMap("select l.email, coalesce(l.phone_number, '') as phone, l.holy_name, "
                + "trim(coalesce(l.last_name,'') || ' ' || l.first_name) as full_name, to_char(l.birth_date, 'DD/MM/YYYY') as birth, "
                + "l.gender, p.name as parish, d.name as deanery from leader l join parish p on p.id=l.parish_id "
                + "join deanery d on d.id=p.deanery_id where lower(l.email)=lower(?)", email);
        String gender = "NAM".equals(String.valueOf(m.get("gender"))) ? "Nam" : "Nữ";
        return new Row(email, phoneOverride != null ? phoneOverride : String.valueOf(m.get("phone")), score, result, review,
                String.valueOf(m.get("holy_name")), String.valueOf(m.get("full_name")), String.valueOf(m.get("birth")), gender, "",
                String.valueOf(m.get("deanery")), String.valueOf(m.get("parish")));
    }

    private Row fullRow(String email, String result, String score) {
        return fullRow(email, null, result, score, "");
    }

    private int countLeaders() {
        return jdbc.queryForObject("select count(*) from leader", Integer.class);
    }

    @Test
    @Order(24)
    void participantFileCreatesMissingLeadersAndOnlyAddsExistingOnesToTheCourse() {
        String course = createPastCourse("Khóa tạo huynh trưởng 2018", 2018, null);
        int leadersBefore = countLeaders();
        String anFirstName = jdbc.queryForObject("select first_name from leader where email='an@example.com'", String.class);

        // File có nhiều lỗi: mọi thứ (kể cả huynh trưởng mới hợp lệ) không được lưu.
        MockMultipartFile bad = excel(PARTICIPANT_HEADERS, List.of(
                participant("moi1@example.com", "0911000111", "Đạt", "Phêrô", "Lê Mới Một", "01/02/1990", "Nam", "", "Thủ Dầu Một", "Búng"),
                participant("khong.thong.tin@example.com", "0911000333", "Đạt", "", "", "", "", "", "", ""),
                participant("thieu@example.com", "0911000444", "Đạt", "Maria", "Thiếu Giới Tính", "01/02/1990", "", "", "Thủ Dầu Một", "Búng"),
                participant("moi1@example.com", "0911000111", "Đạt", "Phêrô", "Lê Mới Một", "01/02/1990", "Nam", "", "Thủ Dầu Một", "Búng"),
                participant("ngoai@example.com", "0911000555", "Đạt", "Giuse", "Ngoài Giáo Phận", "01/02/1990", "Nam", "", "Long Khánh", "Gia Kiệm"),
                participant("saicap@example.com", "0911000666", "Đạt", "Anna", "Sai Cấp Độ", "01/02/1990", "Nữ", "Cấp lạ", "Thủ Dầu Một", "Búng"),
                participant("giaohatla@example.com", "0911000777", "Đạt", "Luca", "Giáo Hạt Lạ", "01/02/1990", "Nam", "", "Giáo hạt lạ", "Búng"),
                participant("thieusdt@example.com", "", "Đạt", "Marco", "Thiếu SĐT", "01/02/1990", "Nam", "", "Thủ Dầu Một", "Búng"),
                participant("an@example.com", "0901234567", "Đạt", "", "", "", "", "", "", "")));
        ImportValidationException exception = assertThrows(ImportValidationException.class,
                () -> participantImportService.importParticipants(course, path(bad), phuCuongAdmin(), JobProgress.NONE));
        var messages = exception.getErrors().stream().map(error -> error.row() + ":" + error.column() + ":" + error.message()).toList();
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("3:Tên thánh:") && m.contains("Bắt buộc")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("3:Giáo xứ:") && m.contains("Bắt buộc")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("4:Giới tính:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("5:Email / Số điện thoại:") && m.contains("nhiều lần")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("6:Giáo xứ:") && m.contains("không thuộc giáo phận của khóa")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("7:Cấp độ:")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("8:Giáo hạt:") && m.contains("chưa có trong hệ thống")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("9:Số điện thoại:") && m.contains("Bắt buộc")), messages.toString());
        // Huynh trưởng đã có hồ sơ vẫn phải điền đủ thông tin.
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("10:Tên thánh:") && m.contains("Bắt buộc")), messages.toString());
        assertEquals(leadersBefore, countLeaders());
        assertEquals(0, jdbc.queryForObject("select count(*) from course_participation where course_id=?", Integer.class, course));

        // File hợp lệ: An đã có hồ sơ (thông tin họ tên trong file khác nhưng bị bỏ qua), hai huynh trưởng mới được tạo.
        MockMultipartFile good = excel(PARTICIPANT_HEADERS, List.of(
                participant("an@example.com", "0901234567", "Đạt", "Tên Khác", "Họ Tên Khác Hoàn Toàn", "09/09/1999", "Nữ", "", "Thủ Dầu Một", "Búng"),
                participant("moi1@example.com", "0911000111", "Đạt", "Phêrô", "Lê Mới Một", "01/02/1990", "Nam", "Huynh trưởng xứ", "Thủ Dầu Một", "Búng"),
                participant("moi2@example.com", "0911000222", "Không đạt", "Maria", "Trần Thị Mới Hai", "03/04/1992", "Nữ", "", "Thủ Dầu Một", "Bà Lụa")));
        ImportSummary summary = participantImportService.importParticipants(course, path(good), phuCuongAdmin(), JobProgress.NONE);

        assertEquals(3, summary.created());
        assertTrue(summary.message().contains("tạo mới 2"), summary.message());
        assertEquals(leadersBefore + 2, countLeaders());
        assertEquals(3, jdbc.queryForObject("select count(*) from course_participation where course_id=?", Integer.class, course));
        // Huynh trưởng đã có không bị sửa thông tin.
        assertEquals(anFirstName, jdbc.queryForObject("select first_name from leader where email='an@example.com'", String.class));
        // Huynh trưởng mới có đủ thông tin, đúng giáo xứ, cấp độ nhập trong file (mặc định NONE) và người tạo.
        // Tách họ tên như khi tạo thủ công: tên là từ cuối, phần còn lại là họ và tên đệm.
        assertEquals("Một", jdbc.queryForObject("select first_name from leader where email='moi1@example.com'", String.class));
        assertEquals("Lê Mới", jdbc.queryForObject("select last_name from leader where email='moi1@example.com'", String.class));
        assertEquals("HT_XU", levelOf("moi1@example.com"));
        assertEquals("NONE", levelOf("moi2@example.com"));
        assertEquals("0911000222", jdbc.queryForObject("select phone_number from leader where email='moi2@example.com'", String.class));
        assertEquals("Búng", jdbc.queryForObject("select p.name from leader l join parish p on p.id=l.parish_id where l.email='moi1@example.com'", String.class));
        assertEquals(USERNAME, jdbc.queryForObject("select created_by from leader where email='moi1@example.com'", String.class));

        // Import lại cùng file: cả ba đã nằm trong khóa nên bị từ chối, không tạo thêm huynh trưởng nào.
        assertThrows(ImportValidationException.class, () -> participantImportService.importParticipants(course, path(good), phuCuongAdmin(), JobProgress.NONE));
        assertEquals(leadersBefore + 2, countLeaders());

        // Lần sau chỉ cần email của huynh trưởng vừa tạo (đã có hồ sơ) cho khóa khác, không cần thông tin tạo.
        String another = createPastCourse("Khóa khác 2017", 2017, null);
        MockMultipartFile existingOnly = excel(PARTICIPANT_HEADERS, List.of(
                participant("moi1@example.com", "0911000111", "Đạt", "Phêrô", "Lê Mới Một", "01/02/1990", "Nam", "", "Thủ Dầu Một", "Búng")));
        assertEquals(1, participantImportService.importParticipants(another, path(existingOnly), phuCuongAdmin(), JobProgress.NONE).created());
        assertEquals(leadersBefore + 2, countLeaders());
    }

    @Test
    @Order(25)
    void leaderCreatedInTheParticipantFileRollsBackWithTheWholeImportWhenTheWriteFails() {
        String course = createPastCourse("Khóa rollback 2016", 2016, null);
        int leadersBefore = countLeaders();
        // Hai dòng cùng số điện thoại nhưng khác email: kiểm tra trong file bắt được trước khi ghi.
        MockMultipartFile file = excel(PARTICIPANT_HEADERS, List.of(
                participant("rb1@example.com", "0988777666", "Đạt", "Phêrô", "Rollback Một", "01/02/1990", "Nam", "", "Thủ Dầu Một", "Búng"),
                participant("rb2@example.com", "0988777666", "Đạt", "Giuse", "Rollback Hai", "01/02/1991", "Nam", "", "Thủ Dầu Một", "Búng")));
        assertThrows(ImportValidationException.class, () -> participantImportService.importParticipants(course, path(file), phuCuongAdmin(), JobProgress.NONE));
        assertEquals(leadersBefore, countLeaders());
        assertEquals(0, countLeaders("rb%@example.com"));
    }

    private static Authentication deaneryAdmin(String username) {
        return admin(username, "training.course.create_past.deanery");
    }

    @Test
    @Order(26)
    void deaneryAdminCreatesPastCoursesOfTheirOwnDeaneryAndImportsLeadersOfThatDeanery() {
        UUID deaneryId = jdbc.queryForObject("select id from deanery where name='Thủ Dầu Một'", UUID.class);
        // Giáo hạt thứ hai cùng giáo phận để kiểm tra phạm vi giáo hạt.
        UUID otherDeaneryId = UUID.randomUUID();
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Bến Cát', 'HĐ', ?, now())", otherDeaneryId, dioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (gen_random_uuid(), 'An Điền', 'CĐ', ?, now())", otherDeaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values ('admin.tdm', now(), ?)", deaneryId);
        jdbc.update("insert into account (username, created_at, deanery_id) values ('admin.bencat', now(), ?)", otherDeaneryId);
        Authentication tdm = deaneryAdmin("admin.tdm");
        Authentication benCat = deaneryAdmin("admin.bencat");

        PastTrainingCourseRequest request = new PastTrainingCourseRequest();
        request.setCourseName("Khóa giáo hạt Thủ Dầu Một 2015");
        request.setHostYear(2015);
        request.setResultingLevel("HT_I");
        TrainingCourseResponse created = pastTrainingCourseService.create(request, tdm);
        String courseCode = created.getCourseCode();

        // Khóa thuộc giáo hạt (gắn giáo hạt, không gắn trực tiếp giáo phận); cấp độ lấy theo danh mục của giáo phận chứa giáo hạt.
        assertTrue(created.getPastCourse());
        assertEquals(deaneryId, created.getDeaneryId());
        assertEquals(null, created.getDioceseId());
        assertEquals("Huynh trưởng cấp I", created.getResultingLevelName());

        // Admin giáo hạt khác và admin giáo phận không thao tác được khóa của giáo hạt này.
        MockMultipartFile file = excel(List.of("Email", "Kết quả *"), List.of(new Row("an@example.com", "Đạt")));
        assertThrows(ApiException.class, () -> participantImportService.importParticipants(courseCode, path(file), benCat, JobProgress.NONE));
        assertThrows(ApiException.class, () -> participantImportService.importParticipants(courseCode, path(file), phuCuongAdmin(), JobProgress.NONE));
        PastTrainingCourseRequest rename = new PastTrainingCourseRequest();
        rename.setCourseName("Đổi tên trái phép");
        rename.setHostYear(2015);
        assertThrows(ApiException.class, () -> pastTrainingCourseService.update(courseCode, rename, benCat));
        assertThrows(ApiException.class, () -> pastTrainingCourseService.update(courseCode, rename, phuCuongAdmin()));

        // Import: huynh trưởng thuộc giáo hạt (An ở Bà Lụa) thêm vào khóa; huynh trưởng mới tạo ở giáo xứ của giáo hạt;
        // giáo xứ của giáo hạt khác (An Điền) không dùng được; huynh trưởng có sẵn ở giáo hạt khác bị từ chối.
        int leadersBefore = countLeaders();
        MockMultipartFile bad = excel(PARTICIPANT_HEADERS, List.of(
                participant("tdm.moi@example.com", "0955000222", "Đạt", "Phêrô", "Mới Thủ Dầu Một", "01/02/1990", "Nam", "", "Bến Cát", "An Điền")));
        ImportValidationException exception = assertThrows(ImportValidationException.class,
                () -> participantImportService.importParticipants(courseCode, path(bad), tdm, JobProgress.NONE));
        assertTrue(exception.getErrors().stream().anyMatch(e -> e.column().equals("Giáo xứ") && e.message().contains("không thuộc giáo hạt của khóa")), exception.getErrors().toString());
        // Một huynh trưởng thuộc giáo hạt khác nhưng cùng giáo phận.
        UUID otherParish = jdbc.queryForObject("select id from parish where name='An Điền'", UUID.class);
        jdbc.update("insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, gender, leader_level, parish_id) "
                + "values (gen_random_uuid(), now(), true, 'Gioan', 'Khác', 'Giáo Hạt', '1990-01-01', 'khac.giaohat@example.com', 'NAM', 'NONE', ?)", otherParish);
        MockMultipartFile otherDeanery = excel(PARTICIPANT_HEADERS, List.of(
                participant("khac.giaohat@example.com", "0955000111", "Đạt", "Gioan", "Khác Giáo Hạt", "01/01/1990", "Nam", "", "Thủ Dầu Một", "Bà Lụa")));
        ImportValidationException scope = assertThrows(ImportValidationException.class,
                () -> participantImportService.importParticipants(courseCode, path(otherDeanery), tdm, JobProgress.NONE));
        assertTrue(scope.getErrors().get(0).message().contains("giáo hạt của khóa"), scope.getErrors().toString());

        MockMultipartFile good = excel(PARTICIPANT_HEADERS, List.of(
                participant("an@example.com", "0901234567", "Đạt", "Phêrô", "Nguyễn Văn An", "07/03/1995", "Nam", "", "Thủ Dầu Một", "Bà Lụa"),
                participant("tdm.moi@example.com", "0955000222", "Đạt", "Phêrô", "Mới Thủ Dầu Một", "01/02/1990", "Nam", "", "Thủ Dầu Một", "Búng")));
        int leadersBeforeGood = countLeaders();
        ImportSummary summary = participantImportService.importParticipants(courseCode, path(good), tdm, JobProgress.NONE);
        assertEquals(2, summary.created());
        assertEquals(leadersBeforeGood + 1, countLeaders());

        // Chứng nhận đề xuất mã giáo hạt (GH + viết tắt giáo hạt), không phải mã giáo phận.
        String code = certificateCodeOf("tdm.moi@example.com", courseCode);
        assertTrue(code.matches("GHTDM-QK-2015-\\d+"), code);

        // Duyệt chứng nhận của khóa giáo hạt: ghi lịch sử cấp độ theo ngày chọn như khóa giáo phận.
        var approved = certificateService.approve(code, approval(LocalDate.of(2015, 12, 12)), deaneryAdmin("admin.tdm"));
        assertEquals("APPROVED", approved.getApprovalStatus().name());
        assertEquals("HT_I", levelOf("tdm.moi@example.com"));
        assertEquals(LocalDate.of(2015, 12, 12), approved.getIssueDate());
    }

    @Test
    @Order(27)
    void avatarZipRunsAfterSuccessfulImportAndImageErrorsDoNotRollbackData() {
        List<String> headers = new java.util.ArrayList<>(LEADER_HEADERS);
        headers.add("Tên file ảnh");
        MockMultipartFile file = excel(headers, List.of(
                new Row("Phêrô", "Avatar Hợp Lệ", "01/01/1990", "Nam", "avatar.valid@example.com", "0966000001", "", "Thủ Dầu Một", "Bà Lụa", "portrait.png"),
                new Row("Maria", "Không Có Ảnh", "02/02/1991", "Nữ", "avatar.none@example.com", "0966000002", "", "Thủ Dầu Một", "Búng", "")));
        var job = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", file,
                imageZip("folder/portrait.png", validPortrait()), newKey(), phuCuongAdmin());
        var done = awaitJob(job.id());

        assertEquals("SUCCEEDED", done.status(), done.errorMessage());
        assertEquals("DONE", done.avatarStatus());
        assertEquals(1, done.avatarProcessed());
        assertEquals(1, done.avatarSucceeded());
        assertEquals(2, countLeaders("avatar.%@example.com"));
        UUID originalAsset = jdbc.queryForObject("select image_asset_id from leader where email='avatar.valid@example.com'", UUID.class);
        assertNotNull(originalAsset);
        assertEquals(null, jdbc.queryForObject("select image_asset_id from leader where email='avatar.none@example.com'", UUID.class));

        // Hồ sơ đã có ảnh: import tham dự vẫn thành công và giữ nguyên ảnh cũ.
        String course = createPastCourse("Khóa avatar 2014", 2014, null);
        List<String> participantHeaders = new java.util.ArrayList<>(PARTICIPANT_HEADERS);
        participantHeaders.add("Tên file ảnh");
        MockMultipartFile participant = excel(participantHeaders, List.of(
                new Row("avatar.valid@example.com", "0966000001", "8", "Đạt", "", "Phêrô", "Avatar Hợp Lệ", "01/01/1990", "Nam", "", "Thủ Dầu Một", "Bà Lụa", "replacement.png"),
                new Row("avatar.participant@example.com", "0966000005", "7", "Đạt", "", "Anna", "Ảnh Từ Khóa", "05/05/1994", "Nữ", "", "Thủ Dầu Một", "Búng", "newcomer.png")));
        var participantJob = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_PARTICIPANTS, course, participant,
                imageZip(java.util.Map.of("replacement.png", validPortrait(), "newcomer.png", validPortrait())), newKey(), phuCuongAdmin());
        var participantDone = awaitJob(participantJob.id());
        assertEquals("SUCCEEDED", participantDone.status(), participantDone.errorMessage());
        assertTrue(participantDone.avatarWarnings().stream().anyMatch(warning -> warning.row() == 2 && warning.fileName().equals("replacement.png")
                && warning.reason().contains("giữ ảnh cũ")), participantDone.avatarWarnings().toString());
        assertEquals(originalAsset, jdbc.queryForObject("select image_asset_id from leader where email='avatar.valid@example.com'", UUID.class));
        assertNotNull(jdbc.queryForObject("select image_asset_id from leader where email='avatar.participant@example.com'", UUID.class));

        // Ảnh hỏng chỉ tạo cảnh báo; hồ sơ vẫn được lưu và không có avatar.
        MockMultipartFile brokenFile = excel(headers, List.of(new Row(
                "Giuse", "Ảnh Bị Hỏng", "03/03/1992", "Nam", "avatar.broken@example.com", "0966000003", "", "Thủ Dầu Một", "Bà Lụa", "broken.jpg")));
        var brokenJob = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", brokenFile,
                imageZip("broken.jpg", new byte[]{1, 2, 3, 4}), newKey(), phuCuongAdmin());
        var brokenDone = awaitJob(brokenJob.id());
        assertEquals("SUCCEEDED", brokenDone.status(), brokenDone.errorMessage());
        assertEquals(1, countLeaders("avatar.broken@example.com"));
        assertEquals(0, brokenDone.avatarSucceeded());
        assertTrue(brokenDone.avatarWarnings().stream().anyMatch(warning -> warning.row() == 2
                && warning.fullName().equals("Ảnh Bị Hỏng") && warning.fileName().equals("broken.jpg")), brokenDone.avatarWarnings().toString());

        // Excel lỗi: transaction rollback và pha cloud không được chạy.
        int assetsBefore = jdbc.queryForObject("select count(*) from media_asset", Integer.class);
        MockMultipartFile invalidExcel = excel(headers, List.of(new Row(
                "", "Excel Sai", "04/04/1993", "Nam", "avatar.excel.bad@example.com", "0966000004", "", "Thủ Dầu Một", "Bà Lụa", "portrait.png")));
        var invalidJob = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", invalidExcel,
                imageZip("portrait.png", validPortrait()), newKey(), phuCuongAdmin());
        var failed = awaitJob(invalidJob.id());
        assertEquals("FAILED", failed.status());
        assertEquals(0, countLeaders("avatar.excel.bad@example.com"));
        assertEquals(assetsBefore, jdbc.queryForObject("select count(*) from media_asset", Integer.class));

        // Có tên ảnh nhưng không gửi ZIP: dữ liệu vẫn lưu, cảnh báo đúng hồ sơ.
        MockMultipartFile noZipFile = excel(headers, List.of(new Row(
                "Luca", "Thiếu File ZIP", "06/06/1995", "Nam", "avatar.nozip@example.com", "0966000006", "", "Thủ Dầu Một", "Bà Lụa", "missing.png")));
        var noZipJob = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "", noZipFile,
                null, newKey(), phuCuongAdmin());
        var noZipDone = awaitJob(noZipJob.id());
        assertEquals("SUCCEEDED", noZipDone.status(), noZipDone.errorMessage());
        assertTrue(noZipDone.avatarWarnings().stream().anyMatch(warning -> warning.row() == 2 && warning.fullName().equals("Thiếu File ZIP")
                && warning.fileName().equals("missing.png") && warning.reason().contains("Không có file ZIP")), noZipDone.avatarWarnings().toString());
    }

    // ================= Cache: dữ liệu đổi thì FE phải thấy dữ liệu mới =================

    private static Authentication updater() {
        return admin(USERNAME, "organization.leader.update.diocese", "organization.leader.read.diocese",
                "organization.leader.create.diocese", "training.course.create_past.diocese");
    }

    private void putSentinel(String... caches) {
        for (String name : caches) {
            cacheManager.getCache(name).put("sentinel", "stale");
        }
    }

    private void assertEvicted(String... caches) {
        for (String name : caches) {
            assertNull(cacheManager.getCache(name).get("sentinel"), "Cache " + name + " chưa được xóa");
        }
    }

    private static final String[] ALL_DEPENDENT_CACHES = {
            phucuong.ht.config.CacheNames.CERTIFICATE_LIST, phucuong.ht.config.CacheNames.CERTIFICATE_DETAIL,
            phucuong.ht.config.CacheNames.EXECUTIVE_ASSIGNMENT_LIST, phucuong.ht.config.CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL,
            phucuong.ht.config.CacheNames.LEADER_LIST, phucuong.ht.config.CacheNames.LEADER_DETAIL,
            phucuong.ht.config.CacheNames.TRAINING_COURSE_LIST, phucuong.ht.config.CacheNames.TRAINING_COURSE_DETAIL};

    @Test
    @Order(28)
    void avatarJobEvictsTheCachedLeaderSoTheNewImageShowsAtOnce() {
        String email = "cache.avatar@example.com";
        excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_LEADERS, "",
                excel(LEADER_HEADERS, List.of(new Row("Tôma", "Cache Avatar", "09/09/1991", "Nam", email, "0966100001", "", "Thủ Dầu Một", "Bà Lụa"))),
                newKey(), phuCuongAdmin());
        awaitIdle();
        UUID leaderId = jdbc.queryForObject("select id from leader where email = ?", UUID.class, email);
        // Đọc trước để chi tiết huynh trưởng (chưa có ảnh) nằm trong cache.
        assertNull(leaderService.findById(leaderId, phuCuongAdmin()).getImageAssetId());

        String course = createPastCourse("Khóa cache avatar 2013", 2013, null);
        List<String> headers = new java.util.ArrayList<>(PARTICIPANT_HEADERS);
        headers.add("Tên file ảnh");
        String[] base = fullRow(email, "Đạt", "7").cells();
        String[] cells = java.util.Arrays.copyOf(base, base.length + 1);
        cells[base.length] = "cache.png";
        var job = excelJobService.createImport(phucuong.ht.common.excel.job.ExcelJobKind.IMPORT_PARTICIPANTS, course,
                excel(headers, List.of(new Row(cells))), imageZip("cache.png", validPortrait()), newKey(), phuCuongAdmin());
        var done = awaitJob(job.id());

        assertEquals("DONE", done.avatarStatus());
        assertEquals(1, done.avatarSucceeded());
        var fresh = leaderService.findById(leaderId, phuCuongAdmin());
        assertNotNull(fresh.getImageAssetId(), "Chi tiết huynh trưởng trong cache vẫn là bản cũ chưa có ảnh");
        assertNotNull(fresh.getImageThumbUrl());
    }

    @Test
    @Order(29)
    void evictionWaitsForTheCommitSoAConcurrentReadCannotReloadStaleData() {
        var cache = cacheManager.getCache(phucuong.ht.config.CacheNames.LEADER_LIST);
        cache.put("tx-key", "cu");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cache.evict("tx-key");
            // Chưa commit: giá trị cũ còn trong cache, bị xóa đúng sau khi commit.
            assertNotNull(cache.get("tx-key"));
        });
        assertNull(cache.get("tx-key"));
        // Rollback thì không được xóa nhầm cache.
        cache.put("tx-key", "cu");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cache.evict("tx-key");
            status.setRollbackOnly();
        });
        assertNotNull(cache.get("tx-key"));
        cache.evict("tx-key");
    }

    @Test
    @Order(30)
    void changingLeaderOrUnitDataEvictsEveryCacheThatEmbedsIt() {
        UUID leaderId = jdbc.queryForObject("select id from leader where email = 'an@example.com'", UUID.class);
        UUID deaneryId = jdbc.queryForObject("select id from deanery where name = 'Thủ Dầu Một'", UUID.class);
        UUID parishId = jdbc.queryForObject("select id from parish where name = 'Bà Lụa'", UUID.class);

        // Đổi thông tin huynh trưởng: chứng nhận và phân công ban điều hành nhúng tên / giáo xứ của người đó.
        putSentinel(ALL_DEPENDENT_CACHES);
        phucuong.ht.modules.leader.model.request.LeaderUpdateRequest leaderUpdate = new phucuong.ht.modules.leader.model.request.LeaderUpdateRequest();
        leaderUpdate.setHolyName("Phêrô");
        leaderService.update(leaderId, leaderUpdate, updater());
        assertEvicted(phucuong.ht.config.CacheNames.CERTIFICATE_LIST, phucuong.ht.config.CacheNames.CERTIFICATE_DETAIL,
                phucuong.ht.config.CacheNames.EXECUTIVE_ASSIGNMENT_LIST, phucuong.ht.config.CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL,
                phucuong.ht.config.CacheNames.LEADER_LIST, phucuong.ht.config.CacheNames.LEADER_DETAIL);

        // Sửa giáo xứ: tên giáo xứ nằm trong huynh trưởng, chứng nhận và phân công.
        putSentinel(ALL_DEPENDENT_CACHES);
        phucuong.ht.modules.parish.model.request.ParishUpdateRequest parishUpdate = new phucuong.ht.modules.parish.model.request.ParishUpdateRequest();
        parishUpdate.setName("Bà Lụa");
        parishUpdate.setChapterName("CĐ");
        parishService.update(parishId, parishUpdate, updater());
        assertEvicted(phucuong.ht.config.CacheNames.LEADER_LIST, phucuong.ht.config.CacheNames.LEADER_DETAIL,
                phucuong.ht.config.CacheNames.CERTIFICATE_LIST, phucuong.ht.config.CacheNames.CERTIFICATE_DETAIL,
                phucuong.ht.config.CacheNames.EXECUTIVE_ASSIGNMENT_LIST, phucuong.ht.config.CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL);

        // Sửa giáo hạt: tên giáo hạt nằm trong giáo xứ, huynh trưởng, khóa, chứng nhận và phân công.
        putSentinel(ALL_DEPENDENT_CACHES);
        putSentinel(phucuong.ht.config.CacheNames.PARISH_LIST, phucuong.ht.config.CacheNames.PARISH_DETAIL);
        phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest deaneryUpdate = new phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest();
        deaneryUpdate.setName("Thủ Dầu Một");
        deaneryUpdate.setAssociationName("HĐ");
        deaneryService.update(deaneryId, deaneryUpdate, updater());
        assertEvicted(ALL_DEPENDENT_CACHES);
        assertEvicted(phucuong.ht.config.CacheNames.PARISH_LIST, phucuong.ht.config.CacheNames.PARISH_DETAIL);

        // Sửa giáo phận: tên giáo phận nằm trong giáo hạt, giáo xứ, khóa, chứng nhận và phân công.
        putSentinel(ALL_DEPENDENT_CACHES);
        putSentinel(phucuong.ht.config.CacheNames.DEANERY_LIST, phucuong.ht.config.CacheNames.DEANERY_DETAIL,
                phucuong.ht.config.CacheNames.PARISH_LIST, phucuong.ht.config.CacheNames.PARISH_DETAIL);
        phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest dioceseUpdate = new phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest();
        dioceseUpdate.setName("Phú Cường");
        dioceseUpdate.setUnionName("Liên đoàn PC");
        dioceseService.update(dioceseId, dioceseUpdate, updater());
        assertEvicted(ALL_DEPENDENT_CACHES);
        assertEvicted(phucuong.ht.config.CacheNames.DEANERY_LIST, phucuong.ht.config.CacheNames.DEANERY_DETAIL,
                phucuong.ht.config.CacheNames.PARISH_LIST, phucuong.ht.config.CacheNames.PARISH_DETAIL);
    }

    @Test
    @Order(31)
    void renamingALeaderLevelEvictsCoursesCertificatesLeadersAndAnalytics() {
        var level = leaderLevelService.list(phuCuongAdmin()).stream().filter(item -> !Boolean.TRUE.equals(item.getIsSystem())).findFirst().orElseThrow();
        putSentinel(phucuong.ht.config.CacheNames.TRAINING_COURSE_LIST, phucuong.ht.config.CacheNames.TRAINING_COURSE_DETAIL,
                phucuong.ht.config.CacheNames.CERTIFICATE_LIST, phucuong.ht.config.CacheNames.LEADER_LIST,
                phucuong.ht.config.CacheNames.ANALYTICS_COURSES, phucuong.ht.config.CacheNames.ANALYTICS_LEADERS);
        phucuong.ht.modules.leaderLevel.model.request.LeaderLevelRequest request = new phucuong.ht.modules.leaderLevel.model.request.LeaderLevelRequest();
        request.setLevelCode(level.getLevelCode());
        request.setLevelName(level.getLevelName());
        leaderLevelService.update(level.getLevelCode(), request, phuCuongAdmin());
        assertEvicted(phucuong.ht.config.CacheNames.TRAINING_COURSE_LIST, phucuong.ht.config.CacheNames.TRAINING_COURSE_DETAIL,
                phucuong.ht.config.CacheNames.CERTIFICATE_LIST, phucuong.ht.config.CacheNames.LEADER_LIST,
                phucuong.ht.config.CacheNames.ANALYTICS_COURSES, phucuong.ht.config.CacheNames.ANALYTICS_LEADERS);
    }

    @Autowired private phucuong.ht.modules.deanery.service.DeaneryImportService deaneryImportService;
    @Autowired private phucuong.ht.modules.parish.service.ParishImportService parishImportService;

    private static Authentication orgAdmin() {
        return admin(USERNAME, "organization.deanery.create.diocese", "organization.parish.create.diocese");
    }

    @Test
    @Order(32)
    void importsDeaneriesAndParishesWithTypeAndParentAndRejectsBadFilesWithoutSavingAnything() {
        // Giáo hạt: chỉ cần tên; trùng giáo hạt đã có hoặc trùng trong file thì báo lỗi và không lưu gì.
        int deaneriesBefore = jdbc.queryForObject("select count(*) from deanery", Integer.class);
        MockMultipartFile badDeaneries = excel(List.of("Tên giáo hạt *", "Email"), List.of(
                new Row("Hạt Nhập Một", ""), new Row("Thủ Dầu Một", ""), new Row("Hạt Nhập Một", "sai-email")));
        ImportValidationException deaneryError = assertThrows(ImportValidationException.class,
                () -> deaneryImportService.importDeaneries(path(badDeaneries), orgAdmin(), JobProgress.NONE));
        assertTrue(deaneryError.getErrors().size() >= 3, deaneryError.getErrors().toString());
        assertEquals(deaneriesBefore, jdbc.queryForObject("select count(*) from deanery", Integer.class));

        MockMultipartFile deaneries = excel(List.of("Tên giáo hạt *"), List.of(new Row("Hạt Nhập Một"), new Row("Hạt Nhập Hai")));
        assertEquals(2, deaneryImportService.importDeaneries(path(deaneries), orgAdmin(), JobProgress.NONE).created());
        assertEquals(deaneriesBefore + 2, jdbc.queryForObject("select count(*) from deanery", Integer.class));
        assertEquals(dioceseId, jdbc.queryForObject("select diocese_id from deanery where name='Hạt Nhập Một'", UUID.class));
        assertTrue(parishImportService.selectableDeaneryNames(orgAdmin()).contains("Hạt Nhập Một"));

        // Giáo xứ: giáo họ nằm TRƯỚC giáo xứ cha cùng file vẫn tạo được; Loại trống là giáo xứ.
        List<String> headers = List.of("Tên giáo xứ *", "Loại", "Tên giáo hạt *", "Giáo xứ cha");
        int parishesBefore = jdbc.queryForObject("select count(*) from parish", Integer.class);
        MockMultipartFile parishes = excel(headers, List.of(
                new Row("Họ Nhập Con", "Giáo họ", "Hạt Nhập Một", "Xứ Nhập Cha"),
                new Row("Xứ Nhập Cha", "", "Hạt Nhập Một", ""),
                new Row("Điểm Nhập Con", "Giáo điểm", "Hạt Nhập Hai", "Xứ Nhập Cha Hai"),
                new Row("Xứ Nhập Cha Hai", "", "Hạt Nhập Hai", "")));
        assertEquals(4, parishImportService.importParishes(path(parishes), orgAdmin(), JobProgress.NONE).created());
        assertEquals(parishesBefore + 4, jdbc.queryForObject("select count(*) from parish", Integer.class));
        assertEquals("PARISH", jdbc.queryForObject("select unit_type from parish where name='Xứ Nhập Cha'", String.class));
        assertEquals("GIAO_HO", jdbc.queryForObject("select unit_type from parish where name='Họ Nhập Con'", String.class));
        assertEquals(jdbc.queryForObject("select id from parish where name='Xứ Nhập Cha'", UUID.class),
                jdbc.queryForObject("select parent_parish_id from parish where name='Họ Nhập Con'", UUID.class));

        // Sai: giáo họ thiếu giáo xứ cha, loại không hợp lệ, giáo hạt chưa có, giáo xứ có cha, tên trùng giáo xứ đã có.
        MockMultipartFile badParishes = excel(headers, List.of(
                new Row("Họ Thiếu Cha", "Giáo họ", "Hạt Nhập Một", ""),
                new Row("Xứ Loại Sai", "Nhà thờ", "Hạt Nhập Một", ""),
                new Row("Xứ Hạt Lạ", "", "Hạt Không Có", ""),
                new Row("Xứ Có Cha", "", "Hạt Nhập Một", "Xứ Nhập Cha"),
                new Row("Xứ Nhập Cha", "", "Hạt Nhập Một", "")));
        ImportValidationException parishError = assertThrows(ImportValidationException.class,
                () -> parishImportService.importParishes(path(badParishes), orgAdmin(), JobProgress.NONE));
        assertEquals(java.util.Set.of("Giáo xứ cha", "Loại", "Tên giáo hạt", "Tên giáo xứ"),
                parishError.getErrors().stream().map(error -> error.column()).collect(java.util.stream.Collectors.toSet()), parishError.getErrors().toString());
        assertEquals(parishesBefore + 4, jdbc.queryForObject("select count(*) from parish", Integer.class));
    }

    private void awaitIdle() {
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end) {
            Integer open = jdbc.queryForObject("select count(*) from excel_job where status in ('QUEUED','RUNNING') or avatar_status in ('PENDING','RUNNING')", Integer.class);
            if (open != null && open == 0) {
                return;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("Tác vụ Excel nền chưa xong sau 20 giây");
    }

}
