package phucuong.ht.modules.position;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V122 biến chức vụ giáo hạt / giáo xứ dùng chung cả giáo phận thành chức vụ riêng từng đơn vị:
 * nhân bản cho từng giáo hạt / giáo xứ và chuyển phân công ban điều hành sang bản của đúng đơn vị.
 */
@Testcontainers
class PositionPerUnitMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Test
    void clonesDeaneryAndParishPositionsPerUnitAndRepointsAssignments() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("121").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        UUID diocese = UUID.randomUUID();
        UUID deaneryA = UUID.randomUUID();
        UUID deaneryB = UUID.randomUUID();
        UUID parishA = UUID.randomUUID();
        jdbc.update("insert into diocese (id, created_at, status, name, union_name) values (?, now(), true, 'Phú Cường', 'Liên đoàn')", diocese);
        jdbc.update("insert into deanery (id, created_at, name, diocese_id) values (?, now(), 'GH A', ?)", deaneryA, diocese);
        jdbc.update("insert into deanery (id, created_at, name, diocese_id) values (?, now(), 'GH B', ?)", deaneryB, diocese);
        jdbc.update("insert into parish (id, created_at, name, deanery_id) values (?, now(), 'GX A', ?)", parishA, deaneryA);

        insertPosition(jdbc, "P-DIO", "Chủ nhiệm", "DIOCESE", diocese);
        insertPosition(jdbc, "P-DEA", "Thư ký", "DEANERY", diocese);
        insertPosition(jdbc, "P-PAR", "Trưởng ban", "PARISH", diocese);
        insertAssignment(jdbc, "DEANERY", deaneryB, "P-DEA");
        insertAssignment(jdbc, "PARISH", parishA, "P-PAR");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("122").load().migrate();

        assertEquals(diocese, jdbc.queryForObject("select unit_id from \"position\" where position_code = 'P-DIO'", UUID.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from \"position\" where position_code in ('P-DEA', 'P-PAR')", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from \"position\" where unit_id = ? and position_type = 'DEANERY' and position_name = 'Thư ký'", Integer.class, deaneryA));
        assertEquals(1, jdbc.queryForObject("select count(*) from \"position\" where unit_id = ? and position_type = 'DEANERY' and position_name = 'Thư ký'", Integer.class, deaneryB));
        assertEquals(1, jdbc.queryForObject("select count(*) from \"position\" where unit_id = ? and position_type = 'PARISH' and position_name = 'Trưởng ban'", Integer.class, parishA));

        String deaneryBPosition = jdbc.queryForObject("select position_id from executive_board_assignment where unit_id = ?", String.class, deaneryB);
        assertEquals(deaneryB, jdbc.queryForObject("select unit_id from \"position\" where position_code = ?", UUID.class, deaneryBPosition));
        String parishPosition = jdbc.queryForObject("select position_id from executive_board_assignment where unit_id = ?", String.class, parishA);
        assertEquals(parishA, jdbc.queryForObject("select unit_id from \"position\" where position_code = ?", UUID.class, parishPosition));
    }

    private static void insertPosition(JdbcTemplate jdbc, String code, String name, String type, UUID dioceseId) {
        jdbc.update("insert into \"position\" (position_code, created_at, status, position_name, position_type, display_order, diocese_id) values (?, now(), true, ?, ?, 1, ?)",
                code, name, type, dioceseId);
    }

    private static void insertAssignment(JdbcTemplate jdbc, String unitType, UUID unitId, String positionCode) {
        jdbc.update("insert into executive_board_assignment (id, created_at, status, unit_type, unit_id, position_id) values (?, now(), true, ?, ?, ?)",
                UUID.randomUUID(), unitType, unitId, positionCode);
    }
}
