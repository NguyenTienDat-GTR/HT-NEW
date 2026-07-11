package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.PermissionAction;
import phucuong.ht.modules.system.model.entity.PermissionModule;
import phucuong.ht.modules.system.model.entity.PermissionResource;
import phucuong.ht.modules.system.model.entity.PermissionScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemPermissionRepositoryTest {

    @Test
    void createPermissionShouldAssignManualIdentifierBeforePersist() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.getReference(PermissionModule.class, "system")).thenReturn(new PermissionModule());
        when(entityManager.getReference(PermissionResource.class, "role")).thenReturn(new PermissionResource());
        when(entityManager.getReference(PermissionAction.class, "create")).thenReturn(new PermissionAction());
        when(entityManager.getReference(PermissionScope.class, "all")).thenReturn(new PermissionScope());
        SystemPermissionRepository repository = new SystemPermissionRepository(entityManager);

        Permission permission = repository.createPermission(
                "system.role.create.all",
                "Create roles",
                "system",
                "role",
                "create",
                "all",
                "Permission description",
                false,
                490,
                "super-admin"
        );

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(entityManager).persist(captor.capture());
        Permission persisted = captor.getValue();

        assertEquals("system.role.create.all", permission.getPermissionCode());
        assertEquals("system.role.create.all", persisted.getPermissionCode());
        assertEquals("Create roles", persisted.getPermissionName());
        assertEquals("super-admin", persisted.getCreatedBy());
        assertEquals("super-admin", persisted.getUpdatedBy());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getUpdatedAt());
        assertTrue(persisted.getStatus());
    }
}
