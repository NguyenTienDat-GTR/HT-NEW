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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V139 thêm ngoại lệ phân quyền vai trò theo đơn vị. Chạy migration trên dữ liệu cũ thật: quyền hiệu lực của mọi tài khoản
 * phải y nguyên sau migration; sau đó kiểm tra thu hồi riêng / cấp riêng chỉ ảnh hưởng đúng đơn vị được đặt.
 */
@Testcontainers
class RolePermissionUnitOverrideMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    private static final String ROLE = "ADMIN_PARISH";

    private final UUID dioceseA = UUID.randomUUID();
    private final UUID dioceseB = UUID.randomUUID();
    private final UUID deaneryX = UUID.randomUUID();
    private final UUID deaneryZ = UUID.randomUUID();
    private final UUID deaneryW = UUID.randomUUID();
    private final UUID parishX1 = UUID.randomUUID();
    private final UUID parishX2 = UUID.randomUUID();
    private final UUID parishZ1 = UUID.randomUUID();
    private final UUID parishW1 = UUID.randomUUID();

    @Test
    void migrationKeepsEffectivePermissionsAndUnitOverridesOnlyAffectTheirUnit() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("138").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedUnits(jdbc);
        for (Object[] account : new Object[][]{
                {"u-x1", null, null, parishX1}, {"u-x2", null, null, parishX2}, {"u-z1", null, null, parishZ1},
                {"u-w1", null, null, parishW1}, {"u-deanery-x", null, deaneryX, null}, {"u-root", null, null, null}}) {
            jdbc.update("insert into account (username, created_at, status, password, diocese_id, deanery_id, parish_id) values (?, now(), true, 'x', ?, ?, ?)",
                    account[0], account[1], account[2], account[3]);
            jdbc.update("insert into account_role (id, username, role_code, created_at, status, is_primary) values (?, ?, ?, now(), true, true)",
                    UUID.randomUUID().toString(), account[0], ROLE);
        }

        String defaultPermission = jdbc.queryForObject(
                "select permission_code from role_permission where role_code = ? and revoked_at is null and status order by permission_code limit 1",
                String.class, ROLE);
        String extraPermission = jdbc.queryForObject("""
                select permission_code from permission p where p.status
                  and not exists (select 1 from role_permission rp where rp.role_code = ? and rp.permission_code = p.permission_code)
                order by permission_code limit 1
                """, String.class, ROLE);

        List<String> rolePermissionsBefore = jdbc.queryForList("select id || role_code || permission_code || status from role_permission order by 1", String.class);
        List<String> effectiveBefore = effective(jdbc);
        assertFalse(effectiveBefore.isEmpty());

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("140").load().migrate();

        assertEquals(rolePermissionsBefore, jdbc.queryForList("select id || role_code || permission_code || status from role_permission order by 1", String.class),
                "role_permission không được đổi");
        assertEquals(effectiveBefore, effective(jdbc), "quyền hiệu lực của mọi tài khoản phải y nguyên khi chưa có ngoại lệ");

        // Thu hồi riêng ở giáo hạt X: chỉ giáo xứ thuộc X mất quyền.
        insert(jdbc, defaultPermission, "DEANERY", null, deaneryX, null, "EXCLUDE", true);
        assertFalse(has(jdbc, "u-x1", defaultPermission));
        assertFalse(has(jdbc, "u-x2", defaultPermission));
        assertTrue(has(jdbc, "u-z1", defaultPermission), "giáo hạt khác không bị ảnh hưởng");
        assertTrue(has(jdbc, "u-w1", defaultPermission), "giáo phận khác không bị ảnh hưởng");
        assertTrue(has(jdbc, "u-root", defaultPermission), "tài khoản không thuộc đơn vị nào theo mặc định");

        // Thu hồi riêng ở một giáo xứ khác: chỉ giáo xứ đó.
        insert(jdbc, defaultPermission, "PARISH", null, null, parishZ1, "EXCLUDE", true);
        assertFalse(has(jdbc, "u-z1", defaultPermission));
        assertTrue(has(jdbc, "u-w1", defaultPermission));

        // Cấp cụ thể hơn thắng: thu hồi cả giáo phận A, phân lại riêng cho một giáo xứ thì chỉ giáo xứ đó có quyền.
        insert(jdbc, defaultPermission, "DIOCESE", dioceseA, null, null, "EXCLUDE", true);
        insert(jdbc, defaultPermission, "PARISH", null, null, parishX1, "GRANT", true);
        assertTrue(has(jdbc, "u-x1", defaultPermission), "cấp riêng ở giáo xứ thắng thu hồi ở giáo phận và giáo hạt");
        assertEquals(1, count(jdbc, "u-x1", defaultPermission));
        assertFalse(has(jdbc, "u-x2", defaultPermission), "giáo xứ khác cùng giáo hạt vẫn bị thu hồi");
        assertFalse(has(jdbc, "u-z1", defaultPermission));
        assertTrue(has(jdbc, "u-w1", defaultPermission), "giáo phận khác không bị ảnh hưởng");

        // Gỡ tạm thu hồi riêng ở giáo xứ Z1 (đặt ở trên) để thử cấp phát ở giáo hạt Z.
        jdbc.update("update role_permission_unit_override set revoked_at = now() where override_type = 'EXCLUDE' and parish_id = ?", parishZ1);
        // Thu hồi riêng ở cấp hẹp hơn cấp phát riêng thì thu hồi thắng.
        insert(jdbc, defaultPermission, "DEANERY", null, deaneryZ, null, "GRANT", true);
        assertTrue(has(jdbc, "u-z1", defaultPermission), "phân lại riêng cho cả giáo hạt Z");
        jdbc.update("update role_permission_unit_override set revoked_at = null where override_type = 'EXCLUDE' and parish_id = ?", parishZ1);
        assertFalse(has(jdbc, "u-z1", defaultPermission), "thu hồi riêng ở giáo xứ Z1 thắng cấp phát ở giáo hạt Z");

        // Thu hồi riêng đã gỡ thì không còn hiệu lực.
        jdbc.update("update role_permission_unit_override set revoked_at = now() where override_type = 'EXCLUDE'");
        assertTrue(has(jdbc, "u-x1", defaultPermission), "gỡ hết thu hồi thì quyền mặc định trở lại");
        assertEquals(1, count(jdbc, "u-x1", defaultPermission), "cấp riêng không tạo dòng trùng với mặc định");
        assertTrue(has(jdbc, "u-x2", defaultPermission));

        // Cấp riêng ở giáo phận B: chỉ tài khoản thuộc B có thêm quyền.
        insert(jdbc, extraPermission, "DIOCESE", dioceseB, null, null, "GRANT", true);
        assertTrue(has(jdbc, "u-w1", extraPermission));
        assertFalse(has(jdbc, "u-x1", extraPermission));
        assertFalse(has(jdbc, "u-root", extraPermission));

        // Cấp riêng hết hạn thì không hiệu lực.
        jdbc.update("update role_permission_unit_override set expires_at = now() - interval '1 day' where permission_code = ? and override_type = 'GRANT'", extraPermission);
        assertFalse(has(jdbc, "u-w1", extraPermission));

        // Đơn vị bị khóa vẫn giữ ngoại lệ (không bị bỏ qua).
        jdbc.update("update deanery set status = false where id = ?", deaneryW);
        insert(jdbc, defaultPermission, "DEANERY", null, deaneryW, null, "EXCLUDE", true);
        assertFalse(has(jdbc, "u-w1", defaultPermission));
    }

    private void seedUnits(JdbcTemplate jdbc) {
        for (Object[] row : new Object[][]{{dioceseA, "Giáo phận A"}, {dioceseB, "Giáo phận B"}}) {
            jdbc.update("insert into diocese (id, created_at, status, name, union_name) values (?, now(), true, ?, 'Liên đoàn')", row[0], row[1]);
        }
        for (Object[] row : new Object[][]{{deaneryX, dioceseA, "GH X"}, {deaneryZ, dioceseA, "GH Z"}, {deaneryW, dioceseB, "GH W"}}) {
            jdbc.update("insert into deanery (id, created_at, name, diocese_id) values (?, now(), ?, ?)", row[0], row[2], row[1]);
        }
        for (Object[] row : new Object[][]{{parishX1, deaneryX, "GX X1"}, {parishX2, deaneryX, "GX X2"},
                {parishZ1, deaneryZ, "GX Z1"}, {parishW1, deaneryW, "GX W1"}}) {
            jdbc.update("insert into parish (id, created_at, name, deanery_id) values (?, now(), ?, ?)", row[0], row[2], row[1]);
        }
    }

    private void insert(JdbcTemplate jdbc, String permission, String level, UUID diocese, UUID deanery, UUID parish, String type, boolean status) {
        jdbc.update("""
                insert into role_permission_unit_override
                  (id, role_code, permission_code, scope_level, diocese_id, deanery_id, parish_id, override_type, assigned_at, created_at, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?)
                """, UUID.randomUUID().toString(), ROLE, permission, level, diocese, deanery, parish, type, status);
    }

    private List<String> effective(JdbcTemplate jdbc) {
        return jdbc.queryForList("select username || '|' || coalesce(role_code, '') || '|' || permission_code || '|' || source_type from v_account_effective_permission order by 1", String.class);
    }

    private boolean has(JdbcTemplate jdbc, String username, String permission) {
        return count(jdbc, username, permission) > 0;
    }

    private int count(JdbcTemplate jdbc, String username, String permission) {
        return jdbc.queryForObject("select count(*) from v_account_effective_permission where username = ? and permission_code = ?", Integer.class, username, permission);
    }
}
