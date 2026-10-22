package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// Cấp giáo phận phân quyền cho admin-deanery / admin-parish, cấp giáo hạt cho admin-parish; không phân quyền cho cấp ngang hoặc cao hơn.
class RolePermissionRoleTierTest {

    private final RoleRepository roleRepository = Mockito.mock(RoleRepository.class);

    private SystemAdminAuthorizationHelper helper() {
        return new SystemAdminAuthorizationHelper(
                new securityUtils(),
                Mockito.mock(AccountScopeValidationHelper.class),
                Mockito.mock(AccountRepository.class),
                roleRepository,
                new CommonValidationHelper(new securityUtils())
        );
    }

    private Role systemRole(String code, int order) {
        Role role = new Role();
        role.setRoleCode(code);
        role.setDisplayOrder(order);
        role.setIsSystem(true);
        return role;
    }

    private UsernamePasswordAuthenticationToken actor(String roleCode, int order, String assignAuthority) {
        when(roleRepository.findByRoleCodeAndStatusTrue(roleCode)).thenReturn(Optional.of(systemRole(roleCode, order)));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("actor", null, List.of(roleCode), List.of(assignAuthority));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void dioceseLevelMayAssignDeaneryAndParishAdminRoles() {
        var auth = actor("ADMIN_DIOCESE", 100, "system.role_permission.assign.diocese");
        assertDoesNotThrow(() -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_DEANERY", 200), "system.role_permission.assign.diocese"));
        assertDoesNotThrow(() -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_PARISH", 300), "system.role_permission.assign.diocese"));
        assertThrows(RuntimeException.class, () -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_DIOCESE", 100), "system.role_permission.assign.diocese"));
    }

    @Test
    void deaneryLevelMayAssignParishAdminOnly() {
        var auth = actor("ADMIN_DEANERY", 200, "system.role_permission.assign.deanery");
        assertDoesNotThrow(() -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_PARISH", 300), "system.role_permission.assign.deanery"));
        assertThrows(RuntimeException.class, () -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_DEANERY", 200), "system.role_permission.assign.deanery"));
        assertThrows(RuntimeException.class, () -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_DIOCESE", 100), "system.role_permission.assign.deanery"));
    }

    @Test
    void parishLevelMayNotAssignAnySystemAdminRole() {
        var auth = actor("ADMIN_PARISH", 300, "system.role_permission.assign.parish");
        assertThrows(RuntimeException.class, () -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_PARISH", 300), "system.role_permission.assign.parish"));
        assertThrows(RuntimeException.class, () -> helper().requireRolePermissionRoleAccess(auth, systemRole("ADMIN_DEANERY", 200), "system.role_permission.assign.parish"));
    }
}
