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
    private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SystemPermissionRepository systemPermissionRepository;

    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Mock
    private AccountManagementAuthorizationHelper accountManagementAuthorizationHelper;

    @Test
    void createShouldAllowDioceseAdminAssigningOwnedPermissionToLowerRole() {
        Role targetRole = role("ADMIN_PARISH", 300);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermission saved = new RolePermission();
        saved.setId("rp-1");
        saved.setRole(targetRole);
        saved.setPermission(permission);
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
    void createShouldAllowDioceseAdminAssigningOwnedPermissionToEqualOrderCustomRole() {
        Role targetRole = role("CUSTOM_DIOCESE", 100);
        targetRole.setIsSystem(false);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermission saved = new RolePermission();
        saved.setId("rp-equal");
        saved.setRole(targetRole);
        saved.setPermission(permission);
        saved.setStatus(true);

        RolePermissionCreateRequest request = request("CUSTOM_DIOCESE", "organization.position.read.diocese");

        when(roleRepository.findById("CUSTOM_DIOCESE")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.create(any(RolePermissionCreateRequest.class), anyString())).thenReturn(saved);

        var response = service().create(request, authentication("organization.position.read.diocese"));

        assertEquals("CUSTOM_DIOCESE", response.getRoleCode());
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
    void createShouldRejectDioceseAdminAssigningPermissionToSystemRole() {
        Role targetRole = role("ADMIN_DEANERY", 200);
        targetRole.setIsSystem(true);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermissionCreateRequest request = request("ADMIN_DEANERY", "organization.position.read.diocese");

        when(roleRepository.findById("ADMIN_DEANERY")).thenReturn(Optional.of(targetRole));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));

        assertThrows(RuntimeException.class, () -> service().create(request, authentication("organization.position.read.diocese")));
    }

    @Test
    void createShouldRejectHigherRoleWhenActorRealRoleIsLowerThanTokenRole() {
        Role targetRole = role("CUSTOM_DIOCESE", 100);
        targetRole.setIsSystem(false);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermissionCreateRequest request = request("CUSTOM_DIOCESE", "organization.position.read.diocese");
        UsernamePasswordAuthenticationToken authentication = authentication("organization.position.read.diocese");

        when(roleRepository.findById("CUSTOM_DIOCESE")).thenReturn(Optional.of(targetRole));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));
        when(accountManagementAuthorizationHelper.currentActorHighestRoleOrder(authentication)).thenReturn(200);

        assertThrows(RuntimeException.class, () -> service(accountManagementAuthorizationHelper).create(request, authentication));
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

    @Test
    void createShouldRejectRoleCreatedByAnotherScope() {
        Role targetRole = role("OTHER_ROLE", 200);
        targetRole.setCreatedBy("someone-else");
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermissionCreateRequest request = request("OTHER_ROLE", "organization.position.read.diocese");
        when(roleRepository.findById("OTHER_ROLE")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));

        var exception = assertThrows(RuntimeException.class, () -> service().create(request, authentication("organization.position.read.diocese")));
        assertEquals("Chỉ được quản lý vai trò do cấp của mình tạo; vai trò khác chỉ được xem", exception.getMessage());
    }

    @Test
    void superAdminCannotManageRoleCreatedByLowerScope() {
        Role targetRole = role("LD_TRUONG", 101);
        targetRole.setIsSystem(false);
        targetRole.setCreatedBy("phucuongadmin");
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermissionCreateRequest request = request("LD_TRUONG", "organization.position.read.diocese");
        when(roleRepository.findById("LD_TRUONG")).thenReturn(Optional.of(targetRole));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("superadmin", null, List.of("SUPER_ADMIN"), List.of());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertThrows(RuntimeException.class, () -> service().create(request, authentication));
    }

    @Test
    void bulkRevokeShouldDeleteAssignmentsOfOwnedRole() {
        Role targetRole = role("CUSTOM_DIOCESE", 100);
        targetRole.setIsSystem(false);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermission assignment = new RolePermission();
        assignment.setRole(targetRole);
        assignment.setPermission(permission);
        assignment.setAssignedBy("granter");
        // Người cấp có role thứ tự 100, cùng cấp với ADMIN_DIOCESE (100): được thu hồi.
        when(accountRepository.findHighestActiveRoleOrder("granter")).thenReturn(100);
        when(roleRepository.findById("CUSTOM_DIOCESE")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(rolePermissionRepository.findByRoleCodeAndPermissionCode("CUSTOM_DIOCESE", "organization.position.read.diocese")).thenReturn(Optional.of(assignment));
        var revokeRequest = new phucuong.ht.modules.system.model.request.RolePermissionRevokeRequest();
        revokeRequest.setRoleCode("CUSTOM_DIOCESE");
        revokeRequest.setPermissionCodes(List.of("organization.position.read.diocese"));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin-dio", "LEADER1", List.of("ADMIN_DIOCESE"),
                List.of("system.role_permission.revoke.diocese", "organization.position.read.diocese"));

        int revoked = service().bulkRevoke(revokeRequest, new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertEquals(1, revoked);
        assertEquals(phucuong.ht.modules.system.model.enums.PermissionRevocationReason.MANUAL, assignment.getRevocationReason());
        org.junit.jupiter.api.Assertions.assertNotNull(assignment.getRevokedAt());
    }

    @Test
    void bulkRevokeShouldRejectActorLowerThanGranterRole() {
        Role targetRole = role("CUSTOM_DIOCESE", 100);
        targetRole.setIsSystem(false);
        Permission permission = permission("organization.position.read.diocese", "diocese");
        RolePermission assignment = new RolePermission();
        assignment.setRole(targetRole);
        assignment.setPermission(permission);
        assignment.setAssignedBy("granter");
        // Người cấp có role thứ tự 50 (cao hơn ADMIN_DIOCESE 100): ADMIN_DIOCESE không được thu hồi.
        when(accountRepository.findHighestActiveRoleOrder("granter")).thenReturn(50);
        when(roleRepository.findById("CUSTOM_DIOCESE")).thenReturn(Optional.of(targetRole));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(role("ADMIN_DIOCESE", 100)));
        when(rolePermissionRepository.findByRoleCodeAndPermissionCode("CUSTOM_DIOCESE", "organization.position.read.diocese")).thenReturn(Optional.of(assignment));
        var revokeRequest = new phucuong.ht.modules.system.model.request.RolePermissionRevokeRequest();
        revokeRequest.setRoleCode("CUSTOM_DIOCESE");
        revokeRequest.setPermissionCodes(List.of("organization.position.read.diocese"));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin-dio", "LEADER1", List.of("ADMIN_DIOCESE"),
                List.of("system.role_permission.revoke.diocese", "organization.position.read.diocese"));

        assertThrows(RuntimeException.class, () -> service().bulkRevoke(revokeRequest, new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        org.junit.jupiter.api.Assertions.assertNull(assignment.getRevokedAt());
    }

    private RolePermissionManagementService service() {
        return service(null);
    }

    private RolePermissionManagementService service(AccountManagementAuthorizationHelper accountManagementAuthorizationHelper) {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                accountRepository,
                roleRepository,
                helper,
                accountManagementAuthorizationHelper
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
        role.setCreatedBy("admin-dio");
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
