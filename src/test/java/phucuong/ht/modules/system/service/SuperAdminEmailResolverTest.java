package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

@Testcontainers
class SuperAdminEmailResolverTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static JdbcTemplate jdbc;
    private static SuperAdminEmailResolver resolver;

    @BeforeAll
    static void setUpDatabase() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("CREATE TABLE leader (id UUID PRIMARY KEY, email VARCHAR(100))");
        jdbc.execute("CREATE TABLE account (username VARCHAR(255) PRIMARY KEY, status BOOLEAN NOT NULL, email VARCHAR(100), leader_id UUID)");
        jdbc.execute("CREATE TABLE account_role (id SERIAL PRIMARY KEY, username VARCHAR(255) NOT NULL, role_code VARCHAR(100) NOT NULL, status BOOLEAN NOT NULL)");
        resolver = new SuperAdminEmailResolver(jdbc);
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE leader, account, account_role");
    }

    @Test
    void usesTheLeaderEmailOfLinkedAccountsAndTheAccountEmailOtherwise() {
        UUID leader = leader("Nguoi.Dung@Example.com");
        account("linked", true, "ignored@example.com", leader, "SUPER_ADMIN", true);
        account("standalone", true, "  Quan.Tri@example.com ", null, "SUPER_ADMIN", true);

        assertEquals(List.of("nguoi.dung@example.com", "quan.tri@example.com"), resolver.emails());
    }

    @Test
    void fallsBackToTheAccountEmailWhenTheLeaderHasNone() {
        account("blank-leader", true, "backup@example.com", leader(" "), "SUPER_ADMIN", true);

        assertEquals(List.of("backup@example.com"), resolver.emails());
    }

    @Test
    void ignoresInactiveAccountsInactiveRolesOtherRolesAndMissingEmails() {
        account("locked", false, "locked@example.com", null, "SUPER_ADMIN", true);
        account("revoked", true, "revoked@example.com", null, "SUPER_ADMIN", false);
        account("diocese", true, "diocese@example.com", null, "ADMIN_DIOCESE", true);
        account("no-email", true, null, null, "SUPER_ADMIN", true);
        account("empty-email", true, "  ", null, "SUPER_ADMIN", true);

        assertEquals(List.of(), resolver.emails());
    }

    @Test
    void listsAnEmailOnlyOnceWhenSeveralAccountsShareIt() {
        account("one", true, "same@example.com", null, "SUPER_ADMIN", true);
        account("two", true, "SAME@example.com", null, "SUPER_ADMIN", true);

        assertEquals(List.of("same@example.com"), resolver.emails());
    }

    private static UUID leader(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO leader (id, email) VALUES (?, ?)", id, email);
        return id;
    }

    private static void account(String username, boolean active, String email, UUID leaderId, String role, boolean roleActive) {
        jdbc.update("INSERT INTO account (username, status, email, leader_id) VALUES (?, ?, ?, ?)", username, active, email, leaderId);
        jdbc.update("INSERT INTO account_role (username, role_code, status) VALUES (?, ?, ?)", username, role, roleActive);
    }
}
