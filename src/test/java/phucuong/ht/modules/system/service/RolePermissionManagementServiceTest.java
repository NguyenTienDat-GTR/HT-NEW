package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.PermissionScope;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.enums.PermissionEffect;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionManagementServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SystemPermissionRepository systemPermissionRepository;

    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void createShouldAllowDioceseAdminAssigningOwnedPermissionToLowerRole() {
        Role targetRole = role("ADMIN_PARISH", 300);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermission saved = new RolePermission();
        saved.setId("rp-1");
        saved.setRole(targetRole);
        saved.setPermission(permission);
        saved.setEffect(PermissionEffect.ALLOW);
        saved.setStatus(true);

        RolePermissionCreateRequest request = request("ADMIN_PARISH", "organization.position.read.diocese");

        when(roleRepository.findById("ADMIN_PARISH")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.create(any(RolePermissionCreateRequest.class), anyString())).thenReturn(saved);

        var response = service().create(request, authentication("organization.position.read.diocese"));

        assertEquals("ADMIN_PARISH", response.getRoleCode());
        assertEquals("organization.position.read.diocese", response.getPermissionCode());
    }

    @Test
    void createShouldRejectDioceseAdminAssigningAllScopePermission() {
        Role targetRole = role("ADMIN_PARISH", 300);
        Permission permission = permission("system.role.read.all", "all");
        RolePermissionCreateRequest request = request("ADMIN_PARISH", "system.role.read.all");

        when(roleRepository.findById("ADMIN_PARISH")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(systemPermissionRepository.findById("system.role.read.all")).thenReturn(Optional.of(permission));

        assertThrows(RuntimeException.class, () -> service().create(request, authentication("system.role.read.all")));
    }

    @Test
    void createShouldRejectDioceseAdminAssigningHigherRole() {
        Role targetRole = role("CUSTOM_HIGH", 50);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermissionCreateRequest request = request("CUSTOM_HIGH", "organization.position.read.diocese");

        when(roleRepository.findById("CUSTOM_HIGH")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));

        assertThrows(RuntimeException.class, () -> service().create(request, authentication("organization.position.read.diocese")));
    }

    @Test
    void createShouldRejectDioceseAdminAssigningPermissionItDoesNotOwn() {
        Role targetRole = role("ADMIN_PARISH", 300);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermissionCreateRequest request = request("ADMIN_PARISH", "organization.position.read.diocese");

        when(roleRepository.findById("ADMIN_PARISH")).thenReturn(Optional.of(targetRole));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));

        assertThrows(RuntimeException.class, () -> service().create(request, authentication()));
    }

    private RolePermissionManagementService service() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                null,
                roleRepository,
                helper
        );
        return new RolePermissionManagementService(
                rolePermissionRepository,
                roleRepository,
                systemPermissionRepository,
                helper,
                authorizationHelper
        );
    }

    private Role role(String roleCode, int displayOrder) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setDisplayOrder(displayOrder);
        role.setStatus(true);
        return role;
    }

    private Permission permission(String permissionCode, String scopeCode) {
        PermissionScope scope = new PermissionScope();
        scope.setScopeCode(scopeCode);
        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        permission.setScope(scope);
        permission.setStatus(true);
        return permission;
    }

    private RolePermissionCreateRequest request(String roleCode, String permissionCode) {
        RolePermissionCreateRequest request = new RolePermissionCreateRequest();
        request.setRoleCode(roleCode);
        request.setPermissionCode(permissionCode);
        request.setEffect(PermissionEffect.ALLOW);
        return request;
    }

    private UsernamePasswordAuthenticationToken authentication(String... permissions) {
        List<String> effectivePermissions = new java.util.ArrayList<>();
        effectivePermissions.add("system.role_permission.assign.diocese");
        effectivePermissions.addAll(List.of(permissions));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                effectivePermissions
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
