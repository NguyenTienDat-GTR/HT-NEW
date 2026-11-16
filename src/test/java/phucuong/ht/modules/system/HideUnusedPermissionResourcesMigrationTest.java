package phucuong.ht.modules.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V142 chỉ tắt hai đối tượng phân loại không còn quyền nào, không xóa dòng nào và không đụng đối tượng khác. */
@Testcontainers
class HideUnusedPermissionResourcesMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Test
    void deactivatesOnlyRequirementAndScoreFormula() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("141").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int totalBefore = jdbc.queryForObject("select count(*) from permission_resource", Integer.class);
        int activeOthersBefore = jdbc.queryForObject(
                "select count(*) from permission_resource where status and resource_code not in ('requirement', 'score_formula')", Integer.class);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("142").load().migrate();

        assertEquals(totalBefore, jdbc.queryForObject("select count(*) from permission_resource", Integer.class), "không xóa dòng nào");
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from permission_resource where status and resource_code in ('requirement', 'score_formula')", Integer.class));
        assertEquals(activeOthersBefore, jdbc.queryForObject(
                "select count(*) from permission_resource where status and resource_code not in ('requirement', 'score_formula')", Integer.class),
                "các đối tượng khác không bị đổi");
    }
}
