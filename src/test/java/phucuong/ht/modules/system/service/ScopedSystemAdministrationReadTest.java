package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;
import phucuong.ht.modules.system.repository.SystemRoleRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopedSystemAdministrationReadTest {

    @Mock
    private SystemRoleRepository roleRepository;

    @Mock
    private SystemPermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private phucuong.ht.modules.system.repository.RolePermissionListingRepository listingRepository;

    @Mock
    private RoleRepository authRoleRepository;

    @Mock
    private SystemAdminAuthorizationHelper authorizationHelper;

    @Mock
    private Authentication authentication;

    @Test
    void roleListFiltersBeforeApplyingPaginationAndDetailRejectsOutOfScope() {
        Role outside = role("outside");
        Role inside = role("inside");
        when(roleRepository.findAll(any(BaseSearchRequest.class)))
                .thenReturn(new PageImpl<>(List.of(outside, inside), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));
        when(authorizationHelper.canReadRole(eq(authentication), eq(outside))).thenReturn(false);
        when(authorizationHelper.canReadRole(eq(authentication), eq(inside))).thenReturn(true);

        BaseSearchRequest request = oneItemPage();
        var response = roleService().findAll(request, authentication);

        assertEquals(1, response.getTotalElements());
        assertEquals("inside", response.getContent().getFirst().getRoleCode());

        when(roleRepository.findById("outside")).thenReturn(Optional.of(outside));
        doThrow(new RuntimeException("forbidden")).when(authorizationHelper).requireRoleReadAccess(authentication, outside);
        assertThrows(RuntimeException.class, () -> roleService().findById("outside", authentication));
    }

    @Test
    void permissionListFiltersBeforeApplyingPaginationAndDetailRejectsOutOfScope() {
        Permission outside = permission("outside");
        Permission inside = permission("inside");
        when(permissionRepository.findAll(any(BaseSearchRequest.class)))
                .thenReturn(new PageImpl<>(List.of(outside, inside), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));
        when(authorizationHelper.canReadPermission(eq(authentication), eq(outside))).thenReturn(false);
        when(authorizationHelper.canReadPermission(eq(authentication), eq(inside))).thenReturn(true);

        var response = permissionService().findAll(oneItemPage(), authentication);

        assertEquals(1, response.getTotalElements());
        assertEquals("inside", response.getContent().getFirst().getPermissionCode());

        when(permissionRepository.findById("outside")).thenReturn(Optional.of(outside));
        doThrow(new RuntimeException("forbidden")).when(authorizationHelper).requirePermissionReadAccess(authentication, outside);
        assertThrows(RuntimeException.class, () -> permissionService().findById("outside", authentication));
    }

    @Test
    void rolePermissionListFiltersBeforeApplyingPaginationAndDetailRejectsOutOfScope() {
        RolePermission outside = rolePermission("outside-role", "outside-permission");
        RolePermission inside = rolePermission("inside-role", "inside-permission");
        when(listingRepository.findAll(any(BaseSearchRequest.class)))
                .thenReturn(new PageImpl<>(List.of(listing(outside), listing(inside)), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));
        when(authorizationHelper.canReadRolePermission(eq(authentication), eq(outside.getRole()), eq(outside.getPermission()))).thenReturn(false);
        when(authorizationHelper.canReadRolePermission(eq(authentication), eq(inside.getRole()), eq(inside.getPermission()))).thenReturn(true);

        var response = rolePermissionService().findAll(oneItemPage(), authentication);

        assertEquals(1, response.getTotalElements());
        assertEquals("inside-role", response.getContent().getFirst().getRoleCode());

        when(rolePermissionRepository.findById("outside-id")).thenReturn(Optional.of(outside));
        doThrow(new RuntimeException("forbidden")).when(authorizationHelper)
                .requireRolePermissionReadAccess(authentication, outside.getRole(), outside.getPermission());
        assertThrows(RuntimeException.class, () -> rolePermissionService().findById("outside-id", authentication));
    }

    private RoleManagementService roleService() {
        return new RoleManagementService(roleRepository, helper(), authorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
    }

    private PermissionManagementService permissionService() {
        return new PermissionManagementService(permissionRepository, helper(), authorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
    }

    private RolePermissionManagementService rolePermissionService() {
        return new RolePermissionManagementService(
                rolePermissionRepository,
                authRoleRepository,
                permissionRepository,
                helper(),
                authorizationHelper
        , org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class),
                new RolePermissionUnitScopeResolver(authorizationHelper, org.mockito.Mockito.mock(phucuong.ht.modules.auth.repository.AccountRepository.class), new securityUtils(), helper()),
                org.mockito.Mockito.mock(RolePermissionUnitOverrideService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.system.repository.RolePermissionUnitOverrideRepository.class),
                listingRepository);
    }

    @Test
    void rolePermissionListCanBeFilteredByValidity() {
        RolePermission active = rolePermission("r1", "p1");
        RolePermission revoked = rolePermission("r2", "p2");
        active.getPermission().setStatus(true);
        revoked.getPermission().setStatus(true);
        var revokedRow = listing(revoked);
        revokedRow.setRevokedAt(java.time.OffsetDateTime.now().minusDays(1));
        revokedRow.setRevocationReason(phucuong.ht.modules.system.model.enums.PermissionRevocationReason.MANUAL);
        RolePermission expired = rolePermission("r3", "p3");
        expired.getPermission().setStatus(true);
        var expiredRow = listing(expired);
        expiredRow.setExpiresAt(java.time.OffsetDateTime.now().minusDays(1));
        when(listingRepository.findAll(any(BaseSearchRequest.class)))
                .thenReturn(new PageImpl<>(List.of(listing(active), revokedRow, expiredRow), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 3));
        when(authorizationHelper.canReadRolePermission(eq(authentication), any(), any())).thenReturn(true);

        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(10);
        request.setFilters(new java.util.LinkedHashMap<>(java.util.Map.of("validity", "ACTIVE")));
        var activeOnly = rolePermissionService().findAll(request, authentication);
        assertEquals(1, activeOnly.getTotalElements());
        assertEquals("r1", activeOnly.getContent().getFirst().getRoleCode());

        request.setFilters(new java.util.LinkedHashMap<>(java.util.Map.of("validity", "INACTIVE")));
        var inactive = rolePermissionService().findAll(request, authentication);
        assertEquals(2, inactive.getTotalElements());

        request.setFilters(new java.util.LinkedHashMap<>(java.util.Map.of("validity", "EXPIRED")));
        assertEquals(1, rolePermissionService().findAll(request, authentication).getTotalElements());
    }

    private phucuong.ht.modules.system.model.entity.RolePermissionListing listing(RolePermission source) {
        var row = new phucuong.ht.modules.system.model.entity.RolePermissionListing();
        row.setId(source.getId());
        row.setRole(source.getRole());
        row.setPermission(source.getPermission());
        row.setEntryType(phucuong.ht.modules.system.model.enums.RolePermissionEntryType.DEFAULT);
        return row;
    }

    private CommonValidationHelper helper() {
        return new CommonValidationHelper(new securityUtils());
    }

    private BaseSearchRequest oneItemPage() {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(1);
        return request;
    }

    private Role role(String roleCode) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setIsSystem(true);
        return role;
    }

    private Permission permission(String permissionCode) {
        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        return permission;
    }

    private RolePermission rolePermission(String roleCode, String permissionCode) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setId("outside-role".equals(roleCode) ? "outside-id" : "inside-id");
        rolePermission.setRole(role(roleCode));
        rolePermission.setPermission(permission(permissionCode));
        return rolePermission;
    }
}
