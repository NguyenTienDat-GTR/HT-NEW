package phucuong.ht.modules.position;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V121 biến chức vụ dùng chung thành chức vụ riêng từng giáo phận. Chạy migration trên dữ liệu cũ thật:
 * mỗi chức vụ được nhân bản cho từng giáo phận và phân công ban điều hành chuyển sang bản của giáo phận chứa đơn vị.
 */
@Testcontainers
class PositionPerDioceseMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Test
    void clonesSharedPositionsPerDioceseAndRepointsAssignmentsByUnitDiocese() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway before = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("120").load();
        before.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        UUID phuCuong = UUID.randomUUID();
        UUID xuanLoc = UUID.randomUUID();
        UUID deaneryPc = UUID.randomUUID();
        UUID parishPc = UUID.randomUUID();
        UUID deaneryXl = UUID.randomUUID();
        for (Object[] row : new Object[][]{{phuCuong, "Phú Cường"}, {xuanLoc, "Xuân Lộc"}}) {
            jdbc.update("insert into diocese (id, created_at, status, name, union_name) values (?, now(), true, ?, 'Liên đoàn')", row[0], row[1]);
        }
        jdbc.update("insert into deanery (id, created_at, name, diocese_id) values (?, now(), 'GH Phú Cường', ?)", deaneryPc, phuCuong);
        jdbc.update("insert into parish (id, created_at, name, deanery_id) values (?, now(), 'GX Phú Cường', ?)", parishPc, deaneryPc);
        jdbc.update("insert into deanery (id, created_at, name, diocese_id) values (?, now(), 'GH Xuân Lộc', ?)", deaneryXl, xuanLoc);

        jdbc.update("insert into \"position\" (position_code, created_at, status, position_name, position_type, display_order) values ('P-OLD', now(), true, 'Chủ nhiệm', 'DIOCESE', 1)");
        jdbc.update("insert into \"position\" (position_code, created_at, status, position_name, position_type, display_order) values ('P-UNUSED', now(), true, 'Thư ký', 'DEANERY', 2)");
        UUID aDiocese = insertAssignment(jdbc, "DIOCESE", phuCuong, "P-OLD");
        UUID aDeanery = insertAssignment(jdbc, "DEANERY", deaneryXl, "P-UNUSED");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("121").load().migrate();

        List<String> codes = jdbc.queryForList("select position_code from \"position\"", String.class);
        assertEquals(4, codes.size(), "2 chức vụ x 2 giáo phận, bản chung cũ bị xóa");
        assertTrue(codes.stream().noneMatch(code -> code.equals("P-OLD") || code.equals("P-UNUSED")));
        assertEquals(0, jdbc.queryForObject("select count(*) from \"position\" where diocese_id is null", Integer.class));

        for (UUID dioceseId : List.of(phuCuong, xuanLoc)) {
            assertEquals(2, jdbc.queryForObject("select count(*) from \"position\" where diocese_id = ?", Integer.class, dioceseId));
            assertEquals(1, jdbc.queryForObject("select count(*) from \"position\" where diocese_id = ? and position_name = 'Chủ nhiệm' and position_type = 'DIOCESE'", Integer.class, dioceseId));
        }

        String pcPosition = jdbc.queryForObject("select position_id from executive_board_assignment where id = ?", String.class, aDiocese);
        assertEquals(phuCuong, jdbc.queryForObject("select diocese_id from \"position\" where position_code = ?", UUID.class, pcPosition));
        assertEquals("Chủ nhiệm", jdbc.queryForObject("select position_name from \"position\" where position_code = ?", String.class, pcPosition));

        String xlPosition = jdbc.queryForObject("select position_id from executive_board_assignment where id = ?", String.class, aDeanery);
        assertEquals(xuanLoc, jdbc.queryForObject("select diocese_id from \"position\" where position_code = ?", UUID.class, xlPosition),
                "phân công ở giáo hạt của Xuân Lộc phải dùng bản chức vụ của Xuân Lộc");
        assertEquals("Thư ký", jdbc.queryForObject("select position_name from \"position\" where position_code = ?", String.class, xlPosition));
        assertNotEquals(pcPosition, xlPosition);
        assertNull(jdbc.queryForObject("select max(position_id) from executive_board_assignment where position_id in ('P-OLD','P-UNUSED')", String.class));
    }

    private static UUID insertAssignment(JdbcTemplate jdbc, String unitType, UUID unitId, String positionCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into executive_board_assignment (id, created_at, status, unit_type, unit_id, position_id) values (?, now(), true, ?, ?, ?)",
                id, unitType, unitId, positionCode);
        return id;
    }
}
