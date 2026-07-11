package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.RoleCreateRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemRoleRepositoryTest {

    @Test
    void createRoleShouldAssignManualIdentifierBeforePersist() {
        EntityManager entityManager = mock(EntityManager.class);
        SystemRoleRepository repository = new SystemRoleRepository(entityManager);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("LD_TRUONG");
        request.setRoleName("Lien doan truong");
        request.setDescription("Role description");
        request.setIsSystem(false);
        request.setDisplayOrder(90);

        Role role = repository.createRole(request, "phucuongadmin");

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(entityManager).persist(captor.capture());
        Role persisted = captor.getValue();

        assertEquals("LD_TRUONG", role.getRoleCode());
        assertEquals("LD_TRUONG", persisted.getRoleCode());
        assertEquals("Lien doan truong", persisted.getRoleName());
        assertEquals("phucuongadmin", persisted.getCreatedBy());
        assertEquals("phucuongadmin", persisted.getUpdatedBy());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getUpdatedAt());
        assertTrue(persisted.getStatus());
    }
}
