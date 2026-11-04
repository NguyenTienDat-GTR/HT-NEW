package phucuong.ht.perf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.trainingCourse.model.request.PastTrainingCourseRequest;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;
import phucuong.ht.modules.training.trainingCourse.service.PastTrainingCourseService;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Đếm số câu SQL chạy trên luồng xử lý của mỗi request đọc phổ biến (Postgres/Redis/RabbitMQ chạy riêng, không đụng DB dev).
 * Khi DB ở xa (ví dụ dev nối DB test qua SSH tunnel, ~60ms mỗi câu), thời gian phản hồi tăng gần tuyến tính theo số câu SQL,
 * nên test giữ mức trần cho từng request để không quay lại tình trạng truy vấn lặp theo từng dòng.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-perf-test",
        "app.security.jwt.audience=ht-management-perf-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false"
})
class SqlPerRequestIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "perf-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "perf-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "perf-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "perf-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "perf-rabbit");
        registry.add("spring.rabbitmq.password", () -> "perf-rabbit-password");
    }

    /** Bọc DataSource để đếm mọi câu SQL (Hibernate lẫn JdbcTemplate) chạy trên luồng đang đo. */
    @TestConfiguration
    static class CountingDataSourceConfig {
        @Bean
        static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource dataSource ? SqlCounter.wrap(dataSource) : bean;
                }
            };
        }
    }

    static final class SqlCounter {
        private static final ThreadLocal<int[]> COUNT = new ThreadLocal<>();
        private static final ThreadLocal<List<String>> SQL = new ThreadLocal<>();
        private static final Set<String> EXECUTE = Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");

        static void start() {
            COUNT.set(new int[1]);
            SQL.set(new ArrayList<>());
        }

        static List<String> statements() {
            List<String> value = SQL.get();
            return value == null ? List.of() : value;
        }

        static int stop() {
            int[] value = COUNT.get();
            COUNT.remove();
            return value == null ? 0 : value[0];
        }

        static DataSource wrap(DataSource target) {
            return proxy(DataSource.class, target, null, (result, method, args) ->
                    result instanceof Connection connection ? proxy(Connection.class, connection, null, SqlCounter::wrapStatement) : result);
        }

        private static Object wrapStatement(Object result, java.lang.reflect.Method method, Object[] args) {
            if (!(result instanceof Statement statement)) {
                return result;
            }
            Class<?> type = result instanceof java.sql.CallableStatement ? java.sql.CallableStatement.class
                    : result instanceof java.sql.PreparedStatement ? java.sql.PreparedStatement.class : Statement.class;
            String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
            return proxy(type, statement, sql, (value, called, calledArgs) -> value);
        }

        interface ResultMapper {
            Object map(Object result, java.lang.reflect.Method method, Object[] args);
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, Object target, String sql, ResultMapper mapper) {
            return (T) Proxy.newProxyInstance(SqlCounter.class.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
                if (Statement.class.isAssignableFrom(type) && EXECUTE.contains(method.getName())) {
                    int[] value = COUNT.get();
                    if (value != null) {
                        value[0]++;
                        String text = sql != null ? sql : args != null && args.length > 0 ? String.valueOf(args[0]) : "?";
                        SQL.get().add(text.replaceAll("\s+", " "));
                    }
                }
                if ("unwrap".equals(method.getName()) && args != null && args[0] instanceof Class<?> wanted && wanted.isInstance(target)) {
                    return target;
                }
                try {
                    return mapper.map(method.invoke(target, args), method, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            });
        }
    }

    private static final String USERNAME = "admin.perf";
    private static final int LEADERS = 25;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private LeaderImportService leaderImportService;
    @Autowired private LeaderLevelService leaderLevelService;
    @Autowired private PastTrainingCourseService pastTrainingCourseService;
    @Autowired private PastCourseParticipantImportService participantImportService;

    private UUID dioceseId;
    private String courseCode;

    private record Row(String... cells) {
    }

    private static Authentication dioceseAdmin() {
        // Principal giống hệt khi request đi qua bộ lọc JWT (một số bước kiểm tra quyền đọc vai trò từ principal).
        phucuong.ht.modules.auth.model.AuthUserDetails principal = phucuong.ht.modules.auth.model.AuthUserDetails.tokenPrincipal(
                USERNAME, null, List.of("ADMIN_DIOCESE"), List.of(
                        "organization.leader.create.diocese", "organization.leader.read.diocese",
                        "training.course.read.diocese", "training.course.create_past.diocese",
                        "training.certificate.read.diocese", "training.certificate.approve.diocese",
                        "training.participation.read.diocese", "training.participation.approve.diocese",
                        "analytics.dashboard.read.diocese", "system.account.manage.diocese",
                        "system.role_permission.read.diocese", "system.role_permission.assign.diocese"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static java.nio.file.Path excel(List<String> headers, List<Row> rows) {
        List<ExcelColumn<Row>> columns = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int index = i;
            columns.add(ExcelColumn.of(headers.get(i), row -> row.cells()[index].isEmpty() ? null : row.cells()[index]));
        }
        try {
            MockMultipartFile file = new MockMultipartFile("file", "import.xlsx", ExcelResponses.XLSX.toString(), ExcelExportWriter.write("Dữ liệu", columns, rows));
            java.nio.file.Path path = java.nio.file.Files.createTempFile("perf-test", ".xlsx");
            java.nio.file.Files.write(path, file.getBytes());
            return path;
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private void seed() {
        dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'Liên đoàn PC', now())", dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (gen_random_uuid(), 'Bà Lụa', 'CĐ', ?, now())", deaneryId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (gen_random_uuid(), 'Búng', 'CĐ', ?, now())", deaneryId);
        jdbc.update("insert into account (username, created_at, diocese_id) values (?, now(), ?)", USERNAME, dioceseId);
        // Vai trò thật trong DB: các bước kiểm tra quyền (cấp vai trò của người xem) truy vấn bảng này như khi chạy thật.
        jdbc.update("insert into account_role (id, username, role_code, created_at, status, is_primary) values (gen_random_uuid(), ?, 'ADMIN_DIOCESE', now(), true, true)", USERNAME);
        leaderLevelService.seedDefaults(dioceseId, "test");

        List<Row> leaders = new ArrayList<>();
        List<Row> participants = new ArrayList<>();
        for (int i = 0; i < LEADERS; i++) {
            String email = "perf" + i + "@example.com";
            String phone = String.format("09770%05d", i);
            String name = "Nguyễn Văn Số" + (char) ('A' + i);
            String parish = i % 2 == 0 ? "Bà Lụa" : "Búng";
            leaders.add(new Row("Phêrô", name, "01/01/1995", "Nam", email, phone, "", "Thủ Dầu Một", parish));
            participants.add(new Row(email, phone, "8", "Đạt", "", "Phêrô", name, "01/01/1995", "Nam", "", "Thủ Dầu Một", parish));
        }
        leaderImportService.importLeaders(excel(List.of("Tên thánh *", "Họ và tên *", "Ngày sinh *", "Giới tính *", "Email *",
                "Số điện thoại *", "Cấp độ", "Giáo hạt *", "Giáo xứ *"), leaders), dioceseAdmin(), JobProgress.NONE);
        // Tạo vài tài khoản gắn huynh trưởng để danh sách tài khoản có dữ liệu.
        jdbc.update("insert into account (username, created_at, diocese_id, leader_id) "
                + "select 'acc' || row_number() over (), now(), ?, id from leader limit 15", dioceseId);

        PastTrainingCourseRequest course = new PastTrainingCourseRequest();
        course.setCourseName("Khóa Huynh trưởng cấp I 2019");
        course.setHostYear(2019);
        course.setResultingLevel("HT_I");
        courseCode = pastTrainingCourseService.create(course, dioceseAdmin()).getCourseCode();
        participantImportService.importParticipants(courseCode, excel(List.of("Email *", "Số điện thoại *", "Tổng điểm *", "Kết quả *",
                "Nhận xét", "Tên thánh *", "Họ và tên *", "Ngày sinh *", "Giới tính *", "Cấp độ", "Giáo hạt *", "Giáo xứ *"), participants),
                dioceseAdmin(), JobProgress.NONE);
        assertEquals(LEADERS, jdbc.queryForObject("select count(*) from course_participation where course_id = ?", Integer.class, courseCode));
    }

    private record Measure(int status, int sql, long ms, List<String> statements, String rows) {
    }

    private static final java.util.regex.Pattern TOTAL = java.util.regex.Pattern.compile("\"totalElements\":(\\d+)");

    private Measure measure(String url) throws Exception {
        SqlCounter.start();
        long started = System.nanoTime();
        MvcResult result = mockMvc.perform(get(url).with(authentication(dioceseAdmin()))).andReturn();
        long ms = (System.nanoTime() - started) / 1_000_000;
        List<String> statements = List.copyOf(SqlCounter.statements());
        java.util.regex.Matcher total = TOTAL.matcher(result.getResponse().getContentAsString());
        return new Measure(result.getResponse().getStatus(), SqlCounter.stop(), ms, statements, total.find() ? total.group(1) : "-");
    }

    @Test
    void readRequestsStayWithinSqlBudget() throws Exception {
        seed();
        // Mức trần số câu SQL của lần gọi đầu (chưa có cache) và lần gọi lại (đã có cache Redis), đo với 25 hồ sơ / 15 tài khoản /
        // ~170 dòng phân quyền. Mức trần không phụ thuộc số dòng: vượt mức thường là có chỗ truy vấn lặp theo từng dòng.
        // Trước khi tối ưu: huynh trưởng 13/9, khóa 18/9, chi tiết khóa 24/15, chứng nhận 53/9, tham dự 54/54,
        // tài khoản 86/86, phân quyền vai trò 431/431, tổng quan 30/9.
        Map<String, int[]> budgets = new LinkedHashMap<>();
        budgets.put("/api/leaders?page=0&size=20", new int[]{10, 6});
        budgets.put("/api/training/courses?page=0&size=20", new int[]{14, 6});
        budgets.put("/api/training/courses/" + courseCode, new int[]{18, 8});
        budgets.put("/api/certificates?page=0&size=20", new int[]{12, 6});
        budgets.put("/api/training/participants?page=0&size=20", new int[]{14, 14});
        budgets.put("/api/system/accounts?page=0&size=20", new int[]{12, 12});
        budgets.put("/api/system/role-permissions?page=0&size=20", new int[]{16, 16});
        budgets.put("/api/analytics/overview", new int[]{30, 6});

        StringBuilder report = new StringBuilder("\n%-70s %6s %6s %6s %6s %6s%n".formatted("request", "status", "sql#1", "sql#2", "ms#1", "rows"));
        List<String> overBudget = new ArrayList<>();
        for (var entry : budgets.entrySet()) {
            Measure first = measure(entry.getKey());
            Measure second = measure(entry.getKey());
            report.append("%-70s %6d %6d %6d %6d %6s%n".formatted(entry.getKey(), first.status(), first.sql(), second.sql(), first.ms(), first.rows()));
            if ("1".equals(System.getenv("PERF_SQL_VERBOSE"))) {
                second.statements().forEach(statement -> report.append("      #2 ").append(statement, 0, Math.min(170, statement.length())).append(System.lineSeparator()));
            }
            if (first.status() != 200) {
                overBudget.add(entry.getKey() + " -> HTTP " + first.status());
            } else if (first.sql() > entry.getValue()[0] || second.sql() > entry.getValue()[1]) {
                overBudget.add(entry.getKey() + " -> " + first.sql() + "/" + second.sql());
            }
        }
        System.out.println("[SQL-PER-REQUEST]" + report);
        assertTrue(overBudget.isEmpty(), "Vượt mức trần số câu SQL: " + overBudget + report);
    }
}
