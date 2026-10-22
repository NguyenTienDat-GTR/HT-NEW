package phucuong.ht.common.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAuditEntityListenerTest {

    private final BaseAuditEntityListener listener = new BaseAuditEntityListener();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void beforeCreateShouldFillAuditFieldsFromLeaderId() {
        authenticate(AuthUserDetails.tokenPrincipal(
                "admin-parish",
                "leader-1",
                List.of("ADMIN_PARISH"),
                List.of("organization.leader.create.parish")
        ));
        TestEntity entity = new TestEntity();

        listener.beforeCreate(entity);

        assertNotNull(entity.getCreatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
        assertEquals("leader-1", entity.getCreatedBy());
        assertEquals("leader-1", entity.getUpdatedBy());
        assertTrue(entity.getStatus());
    }

    @Test
    void beforeCreateShouldUseUsernameWhenPrincipalHasNoLeader() {
        authenticate(AuthUserDetails.tokenPrincipal(
                "phucuongadmin",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of("organization.leader.create.diocese")
        ));
        TestEntity entity = new TestEntity();

        listener.beforeCreate(entity);

        assertEquals("phucuongadmin", entity.getCreatedBy());
        assertEquals("phucuongadmin", entity.getUpdatedBy());
    }

    @Test
    void beforeCreateShouldUseSuperAdminActor() {
        authenticate(AuthUserDetails.tokenPrincipal(
                "superadmin",
                null,
                List.of("SUPER_ADMIN"),
                List.of("system.role.create.all")
        ));
        TestEntity entity = new TestEntity();

        listener.beforeCreate(entity);

        assertEquals("super-admin", entity.getCreatedBy());
        assertEquals("super-admin", entity.getUpdatedBy());
    }

    @Test
    void beforeCreateShouldPreserveExplicitAuditActor() {
        authenticate(AuthUserDetails.tokenPrincipal(
                "admin-parish",
                "leader-1",
                List.of("ADMIN_PARISH"),
                List.of()
        ));
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        TestEntity entity = new TestEntity();
        entity.setCreatedAt(createdAt);
        entity.setCreatedBy("batch-import");

        listener.beforeCreate(entity);

        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(createdAt, entity.getUpdatedAt());
        assertEquals("batch-import", entity.getCreatedBy());
        assertEquals("batch-import", entity.getUpdatedBy());
    }

    @Test
    void beforeUpdateShouldRefreshTimestampAndFillMissingActor() {
        authenticate(AuthUserDetails.tokenPrincipal(
                "phucuongadmin",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of()
        ));
        OffsetDateTime oldUpdatedAt = OffsetDateTime.now().minusDays(1);
        TestEntity entity = new TestEntity();
        entity.setUpdatedAt(oldUpdatedAt);

        listener.beforeUpdate(entity);

        assertTrue(entity.getUpdatedAt().isAfter(oldUpdatedAt));
        assertEquals("phucuongadmin", entity.getUpdatedBy());
    }

    @Test
    void beforeUpdateShouldOverrideStaleActorWhenAuthenticated() {
        authenticate(AuthUserDetails.tokenPrincipal(
                "admin-parish",
                "leader-2",
                List.of("ADMIN_PARISH"),
                List.of()
        ));
        TestEntity entity = new TestEntity();
        entity.setCreatedBy("leader-1");
        entity.setUpdatedBy("leader-1");

        listener.beforeUpdate(entity);

        assertEquals("leader-2", entity.getUpdatedBy());
        assertEquals("leader-1", entity.getCreatedBy());
    }

    @Test
    void beforeUpdateShouldPreserveExplicitActorWithoutAuthentication() {
        TestEntity entity = new TestEntity();
        entity.setUpdatedBy("SYSTEM_AUTO_APPROVAL");

        listener.beforeUpdate(entity);

        assertNotNull(entity.getUpdatedAt());
        assertEquals("SYSTEM_AUTO_APPROVAL", entity.getUpdatedBy());
    }

    @Test
    void beforeUpdateShouldFallBackToSystemWhenBlankWithoutAuthentication() {
        TestEntity entity = new TestEntity();

        listener.beforeUpdate(entity);

        assertEquals("SYSTEM", entity.getUpdatedBy());
    }

    @Test
    void beforeCreateShouldUseSystemActorWithoutAuthentication() {
        TestEntity entity = new TestEntity();

        listener.beforeCreate(entity);

        assertEquals("SYSTEM", entity.getCreatedBy());
        assertEquals("SYSTEM", entity.getUpdatedBy());
    }

    private void authenticate(AuthUserDetails principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private static class TestEntity extends BaseModel {
    }
}
