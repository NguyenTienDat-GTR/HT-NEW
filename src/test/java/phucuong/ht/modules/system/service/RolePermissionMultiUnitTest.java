package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.PermissionScope;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermissionUnitOverride;
import phucuong.ht.modules.system.model.enums.RolePermissionOverrideType;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionRevokeRequest;
import phucuong.ht.modules.system.repository.RolePermissionListingRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.RolePermissionUnitOverrideRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.Actor;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.ActorTier;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.UnitScope;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chọn nhiều nơi áp dụng cùng lúc khi phân quyền / thu hồi quyền của vai trò. */
class RolePermissionMultiUnitTest {

    private static final String PERM = "organization.leader.read.parish";

    private final RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final SystemPermissionRepository permissionRepository = mock(SystemPermissionRepository.class);
    private final SystemAdminAuthorizationHelper authorizationHelper = mock(SystemAdminAuthorizationHelper.class);
    private final RolePermissionUnitScopeResolver resolver = mock(RolePermissionUnitScopeResolver.class);
    private final RolePermissionUnitOverrideService overrideService = mock(RolePermissionUnitOverrideService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final CommonValidationHelper validation = mock(CommonValidationHelper.class, org.mockito.Mockito.CALLS_REAL_METHODS);

    private final UUID diocese = UUID.randomUUID();
    private final UUID deaneryA = UUID.randomUUID();
    private final UUID deaneryB = UUID.randomUUID();
    private final Role role = new Role();
    private final Permission permission = new Permission();

    RolePermissionMultiUnitTest() {
        role.setRoleCode("ADMIN_PARISH");
        role.setStatus(true);
        role.setIsSystem(true);
        permission.setPermissionCode(PERM);
        permission.setStatus(true);
        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("parish");
        permission.setScope(scope);
    }

    private RolePermissionManagementService service() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        when(roleRepository.findById("ADMIN_PARISH")).thenReturn(Optional.of(role));
        when(permissionRepository.findById(PERM)).thenReturn(Optional.of(permission));
        when(resolver.actor(any(), anyBoolean())).thenReturn(new Actor(ActorTier.DIOCESE, diocese, null));
        when(authorizationHelper.hasAuthority(any(), any())).thenReturn(false);
        RolePermissionManagementService service = new RolePermissionManagementService(
                rolePermissionRepository, roleRepository, permissionRepository, helperWithActor(helper), authorizationHelper,
                mock(AuditActorNameResolver.class), resolver, overrideService,
                mock(RolePermissionUnitOverrideRepository.class), mock(RolePermissionListingRepository.class));
        return service;
    }

    private CommonValidationHelper helperWithActor(CommonValidationHelper helper) {
        return new CommonValidationHelper(new securityUtils()) {
            @Override
            public String requireAuditActor(Authentication authentication, String message) {
                return "admin";
            }
        };
    }

    private UnitScope deanery(UUID id, String name) {
        return new UnitScope(RolePermissionScopeLevel.DEANERY, diocese, "A", id, name, null, null);
    }

    private RolePermissionUnitOverride overrideRow(RolePermissionOverrideType type) {
        RolePermissionUnitOverride row = new RolePermissionUnitOverride();
        row.setId(UUID.randomUUID().toString());
        row.setRole(role);
        row.setPermission(permission);
        row.setOverrideType(type);
        row.setScopeLevel(RolePermissionScopeLevel.DEANERY);
        return row;
    }

    @Test
    void bulkRevokeAppliesToEveryChosenUnitAndSkipsUnitsThatDoNotHaveThePermission() {
        UnitScope a = deanery(deaneryA, "A");
        UnitScope b = deanery(deaneryB, "B");
        when(resolver.resolveAll(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(a, b));
        RolePermissionManagementService service = service();
        when(overrideService.revoke(role, permission, a, "admin")).thenReturn(new RolePermissionUnitOverrideService.Change(overrideRow(RolePermissionOverrideType.EXCLUDE)));
        when(overrideService.revoke(role, permission, b, "admin"))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "chưa có quyền"));

        RolePermissionRevokeRequest request = new RolePermissionRevokeRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCodes(List.of(PERM));
        request.setDeaneryIds(List.of(deaneryA, deaneryB));

        assertEquals(1, service.bulkRevoke(request, authentication));
        verify(overrideService).revoke(role, permission, a, "admin");
        verify(overrideService).revoke(role, permission, b, "admin");
    }

    @Test
    void bulkRevokeFailsWhenNoChosenUnitHadThePermission() {
        UnitScope a = deanery(deaneryA, "A");
        UnitScope b = deanery(deaneryB, "B");
        when(resolver.resolveAll(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(a, b));
        RolePermissionManagementService service = service();
        when(overrideService.revoke(any(), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "chưa có quyền"));

        RolePermissionRevokeRequest request = new RolePermissionRevokeRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCodes(List.of(PERM));
        request.setDeaneryIds(List.of(deaneryA, deaneryB));

        assertThrows(ApiException.class, () -> service.bulkRevoke(request, authentication));
    }

    @Test
    void bulkAssignAppliesToEveryChosenUnitAndSkipsUnitsThatAlreadyHaveThePermission() {
        UnitScope a = deanery(deaneryA, "A");
        UnitScope b = deanery(deaneryB, "B");
        when(resolver.resolveAll(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(a, b));
        RolePermissionManagementService service = service();
        when(overrideService.assign(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UnitScope unit = invocation.getArgument(2);
                    if (unit == b) {
                        throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "đã có quyền");
                    }
                    return new RolePermissionUnitOverrideService.Change(overrideRow(RolePermissionOverrideType.GRANT));
                });
        // toResponse của dòng vừa thay đổi cần chuỗi đơn vị: UnitScope của giáo hạt A đã đủ.
        when(resolver.canManageRow(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(resolver.unitNames(any())).thenReturn(java.util.Map.of());

        RolePermissionBulkRequest request = new RolePermissionBulkRequest();
        request.setRoleCode("ADMIN_PARISH");
        request.setPermissionCodes(List.of(PERM));
        request.setDeaneryIds(List.of(deaneryA, deaneryB));

        assertEquals(1, service.bulkAssign(request, authentication).size());
        verify(overrideService, never()).revoke(any(), any(), any(), any());
    }

    @Test
    void assignableListShowsPermissionsMissingInAtLeastOneChosenUnit() {
        UnitScope a = deanery(deaneryA, "A");
        UnitScope b = deanery(deaneryB, "B");
        when(resolver.resolveAll(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(a, b));
        RolePermissionManagementService service = service();
        when(permissionRepository.findActivePermissions()).thenReturn(List.of(permission));
        when(resolver.permissionAllowed(any(), any())).thenReturn(true);
        // A đã có quyền, B chưa có: vẫn phải liệt kê để phân cho B.
        when(overrideService.effectiveCodes(role, a)).thenReturn(Set.of(PERM));
        when(overrideService.effectiveCodes(role, b)).thenReturn(Set.of());

        assertEquals(1, service.findAssignablePermissions("ADMIN_PARISH", null, List.of(deaneryA, deaneryB), null, authentication).size());

        when(overrideService.effectiveCodes(role, b)).thenReturn(Set.of(PERM));
        assertEquals(0, service.findAssignablePermissions("ADMIN_PARISH", null, List.of(deaneryA, deaneryB), null, authentication).size());
    }
}
