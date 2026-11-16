package phucuong.ht.modules.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V141 chuyển các thu hồi quyền toàn hệ thống (làm trước khi có ngoại lệ theo đơn vị) của ADMIN_DIOCESE / ADMIN_DEANERY / ADMIN_PARISH
 * thành thu hồi riêng cho giáo phận Phú Cường và khôi phục bộ quyền mặc định. Chạy trên dữ liệu cũ thật, không đổi quyền ở Phú Cường.
 */
@Testcontainers
class GlobalRevocationConversionMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    private DataSource database(String name) {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace("/ht_management", "/" + name), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private JdbcTemplate create(String name) {
        new JdbcTemplate(database("ht_management")).execute("create database " + name);
        return new JdbcTemplate(database(name));
    }

    private void migrate(String name, String target) {
        Flyway.configure().dataSource(database(name)).locations("classpath:db/migration").target(target).load().migrate();
    }

    private UUID diocese(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into diocese (id, created_at, status, name, union_name) values (?, now(), true, ?, 'Liên đoàn')", id, name);
        return id;
    }

    private void account(JdbcTemplate jdbc, String username, String role, UUID dioceseId) {
        jdbc.update("insert into account (username, created_at, status, password, diocese_id) values (?, now(), true, 'x', ?)", username, dioceseId);
        jdbc.update("insert into account_role (id, username, role_code, created_at, status, is_primary) values (?, ?, ?, now(), true, true)",
                UUID.randomUUID().toString(), username, role);
    }

    private boolean has(JdbcTemplate jdbc, String username, String permission) {
        return jdbc.queryForObject("select count(*) from v_account_effective_permission where username = ? and permission_code = ?",
                Integer.class, username, permission) > 0;
    }

    @Test
    void convertsManualGlobalRevocationsIntoPhuCuongOverrideAndRestoresTheDefaults() {
        JdbcTemplate jdbc = create("conv_ok");
        migrate("conv_ok", "140");
        UUID phuCuong = diocese(jdbc, "Phú Cường");
        UUID xuanLoc = diocese(jdbc, "Xuân Lộc");
        account(jdbc, "pc-parish", "ADMIN_PARISH", phuCuong);
        account(jdbc, "xl-parish", "ADMIN_PARISH", xuanLoc);
        account(jdbc, "pc-diocese", "ADMIN_DIOCESE", phuCuong);

        List<String> parishPermissions = jdbc.queryForList(
                "select permission_code from role_permission where role_code = 'ADMIN_PARISH' and revoked_at is null order by permission_code limit 3", String.class);
        String manual = parishPermissions.get(0);
        String expired = parishPermissions.get(1);
        String untouched = parishPermissions.get(2);
        String dioceseManual = jdbc.queryForObject(
                "select permission_code from role_permission where role_code = 'ADMIN_DIOCESE' and revoked_at is null order by permission_code limit 1", String.class);

        // Thu hồi cũ trên production: toàn hệ thống.
        jdbc.update("update role_permission set revoked_at = now() - interval '5 days', revoked_by = 'super-admin', revocation_reason = 'MANUAL' where role_code = 'ADMIN_PARISH' and permission_code = ?", manual);
        jdbc.update("update role_permission set revoked_at = now() - interval '3 days', revoked_by = 'system', revocation_reason = 'EXPIRED' where role_code = 'ADMIN_PARISH' and permission_code = ?", expired);
        jdbc.update("update role_permission set revoked_at = now() - interval '2 days', revoked_by = 'super-admin', revocation_reason = 'MANUAL' where role_code = 'ADMIN_DIOCESE' and permission_code = ?", dioceseManual);
        // Vai trò tự tạo của một đơn vị: không được đụng tới.
        jdbc.update("insert into role (role_code, role_name, is_system, display_order, status, created_at, created_by) values ('CUSTOM_PC', 'Tự tạo', false, 900, true, now(), 'phucuongadmin')");
        jdbc.update("insert into role_permission (id, role_code, permission_code, assigned_by, assigned_at, status, created_at, created_by, revoked_at, revoked_by, revocation_reason) values (?, 'CUSTOM_PC', ?, 'x', now(), true, now(), 'x', now(), 'x', 'MANUAL')",
                UUID.randomUUID().toString(), untouched);

        assertFalse(has(jdbc, "pc-parish", manual));
        assertFalse(has(jdbc, "xl-parish", manual));
        List<String> effectiveBefore = jdbc.queryForList("select username || '|' || permission_code from v_account_effective_permission where username = 'pc-parish' order by 1", String.class);

        migrate("conv_ok", "141");

        // Phú Cường giữ nguyên: quyền của mọi tài khoản ở đó không đổi.
        assertEquals(effectiveBefore, jdbc.queryForList("select username || '|' || permission_code from v_account_effective_permission where username = 'pc-parish' order by 1", String.class));
        assertFalse(has(jdbc, "pc-parish", manual));
        assertFalse(has(jdbc, "pc-diocese", dioceseManual));
        // Bộ quyền mặc định và giáo phận khác có lại quyền.
        assertTrue(has(jdbc, "xl-parish", manual));
        assertNull(jdbc.queryForObject("select revoked_at from role_permission where role_code = 'ADMIN_PARISH' and permission_code = ?", java.time.OffsetDateTime.class, manual));
        // Hết hạn và vai trò tự tạo giữ nguyên.
        assertNotNull(jdbc.queryForObject("select revoked_at from role_permission where role_code = 'ADMIN_PARISH' and permission_code = ?", java.time.OffsetDateTime.class, expired));
        assertNotNull(jdbc.queryForObject("select revoked_at from role_permission where role_code = 'CUSTOM_PC'", java.time.OffsetDateTime.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from role_permission_unit_override where role_code = 'CUSTOM_PC' or permission_code = ?", Integer.class, expired));
        // Hai thu hồi riêng đúng giáo phận, đúng loại, giữ người và thời điểm thu hồi cũ.
        assertEquals(2, jdbc.queryForObject("select count(*) from role_permission_unit_override where diocese_id = ? and override_type = 'EXCLUDE' and scope_level = 'DIOCESE' and assigned_by = 'super-admin' and revoked_at is null", Integer.class, phuCuong));
        assertEquals(2, jdbc.queryForObject("select count(*) from role_permission_unit_override", Integer.class));
    }

    @Test
    void doesNothingWhenPhuCuongCannotBeIdentified() {
        JdbcTemplate jdbc = create("conv_skip");
        migrate("conv_skip", "140");
        diocese(jdbc, "Xuân Lộc");
        String permission = jdbc.queryForObject(
                "select permission_code from role_permission where role_code = 'ADMIN_PARISH' and revoked_at is null order by permission_code limit 1", String.class);
        jdbc.update("update role_permission set revoked_at = now(), revoked_by = 'super-admin', revocation_reason = 'MANUAL' where role_code = 'ADMIN_PARISH' and permission_code = ?", permission);

        migrate("conv_skip", "141");

        assertNotNull(jdbc.queryForObject("select revoked_at from role_permission where role_code = 'ADMIN_PARISH' and permission_code = ?", java.time.OffsetDateTime.class, permission),
                "không có Phú Cường thì giữ nguyên thu hồi, không cấp lại quyền cho mọi nơi");
        assertEquals(0, jdbc.queryForObject("select count(*) from role_permission_unit_override", Integer.class));
    }
}
