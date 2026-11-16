package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.PermissionScope;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.entity.RolePermissionUnitOverride;
import phucuong.ht.modules.system.model.enums.RolePermissionOverrideType;
import phucuong.ht.modules.system.model.enums.RolePermissionScopeLevel;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.RolePermissionUnitOverrideRepository;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.Actor;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.ActorTier;
import phucuong.ht.modules.system.service.RolePermissionUnitScopeResolver.UnitScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionUnitOverrideServiceTest {

    private static final String PERM = "organization.position.read.parish";

    @Mock
    private RolePermissionUnitOverrideRepository overrideRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private final UUID diocese = UUID.randomUUID();
    private final UUID deanery = UUID.randomUUID();
    private final UUID parish = UUID.randomUUID();

    private RolePermissionUnitOverrideService service() {
        return new RolePermissionUnitOverrideService(overrideRepository, rolePermissionRepository, new CommonValidationHelper(new securityUtils()));
    }

    private UnitScope parishScope() {
        return new UnitScope(RolePermissionScopeLevel.PARISH, diocese, "A", deanery, "X", parish, "X1");
    }

    private Role role(String code) {
        Role role = new Role();
        role.setRoleCode(code);
        role.setIsSystem(true);
        return role;
    }

    private Permission permission(String scopeCode) {
        Permission permission = new Permission();
        permission.setPermissionCode(PERM);
        PermissionScope scope = new PermissionScope();
        scope.setScopeCode(scopeCode);
        permission.setScope(scope);
        return permission;
    }

    private void defaultActive(boolean active) {
        if (active) {
            RolePermission rp = new RolePermission();
            rp.setStatus(true);
            when(rolePermissionRepository.findByRoleCodeAndPermissionCode("ADMIN_PARISH", PERM)).thenReturn(Optional.of(rp));
        } else {
            when(rolePermissionRepository.findByRoleCodeAndPermissionCode("ADMIN_PARISH", PERM)).thenReturn(Optional.empty());
        }
    }

    @Test
    void revokeCreatesExcludeWhenDefaultGrantsThePermission() {
        defaultActive(true);
        when(overrideRepository.find(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(overrideRepository.findActiveInChain(any(), any(), anyCollection(), any())).thenReturn(List.of());

        var change = service().revoke(role("ADMIN_PARISH"), permission("parish"), parishScope(), "admin");

        assertEquals(RolePermissionOverrideType.EXCLUDE, change.entity().getOverrideType());
        assertEquals(RolePermissionScopeLevel.PARISH, change.entity().getScopeLevel());
        assertEquals(parish, change.entity().getParishId());
        assertNull(change.entity().getDioceseId());
        verify(overrideRepository).save(change.entity());
    }

    @Test
    void revokeWithoutAnythingToRevokeIsNotFound() {
        defaultActive(false);
        when(overrideRepository.find(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(overrideRepository.findActiveInChain(any(), any(), anyCollection(), any())).thenReturn(List.of());

        assertThrows(ApiException.class, () -> service().revoke(role("ADMIN_PARISH"), permission("parish"), parishScope(), "admin"));
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void assignIsBlockedWhenAHigherLevelExcludedThePermission() {
        RolePermissionUnitOverride upper = new RolePermissionUnitOverride();
        upper.setScopeLevel(RolePermissionScopeLevel.DIOCESE);
        when(overrideRepository.findActiveInChain(any(), any(), anyCollection(), eq(RolePermissionOverrideType.EXCLUDE))).thenReturn(List.of(upper));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().assign(role("ADMIN_PARISH"), permission("parish"), parishScope(), null, null, null, "admin"));
        assertTrue(ex.getMessage().contains("giáo phận"));
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void assignLiftsOwnExcludeWithoutCreatingAGrantWhenDefaultStillGrants() {
        RolePermissionUnitOverride exclude = new RolePermissionUnitOverride();
        exclude.setStatus(true);
        exclude.setOverrideType(RolePermissionOverrideType.EXCLUDE);
        defaultActive(true);
        when(overrideRepository.findActiveInChain(any(), any(), anyCollection(), any())).thenReturn(List.of());
        when(overrideRepository.find(any(), any(), any(), eq(RolePermissionOverrideType.EXCLUDE))).thenReturn(Optional.of(exclude));
        when(overrideRepository.find(any(), any(), any(), eq(RolePermissionOverrideType.GRANT))).thenReturn(Optional.empty());

        var change = service().assign(role("ADMIN_PARISH"), permission("parish"), parishScope(), null, null, null, "admin");

        assertNotNull(change.entity().getRevokedAt(), "thu hồi riêng được gỡ");
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void assignCreatesGrantWhenNobodyGrantsThePermission() {
        defaultActive(false);
        when(overrideRepository.findActiveInChain(any(), any(), anyCollection(), any())).thenReturn(List.of());
        when(overrideRepository.find(any(), any(), any(), any())).thenReturn(Optional.empty());

        var change = service().assign(role("ADMIN_PARISH"), permission("parish"), parishScope(), "ghi chú", null, null, "admin");

        assertEquals(RolePermissionOverrideType.GRANT, change.entity().getOverrideType());
        verify(overrideRepository).save(change.entity());
    }

    @Test
    void assignWhenAlreadyEffectiveIsConflict() {
        defaultActive(true);
        when(overrideRepository.findActiveInChain(any(), any(), anyCollection(), any())).thenReturn(List.of());
        when(overrideRepository.find(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThrows(ApiException.class,
                () -> service().assign(role("ADMIN_PARISH"), permission("parish"), parishScope(), null, null, null, "admin"));
    }

    // ---- Quy tắc của người thao tác ----

    private RolePermissionUnitScopeResolver resolver() {
        return new RolePermissionUnitScopeResolver(mock(SystemAdminAuthorizationHelper.class),
                mock(phucuong.ht.modules.auth.repository.AccountRepository.class), new securityUtils(),
                new CommonValidationHelper(new securityUtils()));
    }

    @Test
    void dioceseAdminMayOnlyTouchDeaneryAndParishScopedPermissions() {
        var resolver = resolver();
        Actor dioceseAdmin = new Actor(ActorTier.DIOCESE, diocese, null);
        assertTrue(resolver.permissionAllowed(dioceseAdmin, permission("deanery")));
        assertTrue(resolver.permissionAllowed(dioceseAdmin, permission("parish")));
        assertFalse(resolver.permissionAllowed(dioceseAdmin, permission("diocese")));
        assertFalse(resolver.permissionAllowed(dioceseAdmin, permission("all")));
        assertFalse(resolver.permissionAllowed(dioceseAdmin, permission("own")));
    }

    @Test
    void deaneryAdminMayOnlyTouchParishScopedPermissionsInOwnDeanery() {
        var resolver = resolver();
        Actor deaneryAdmin = new Actor(ActorTier.DEANERY, diocese, deanery);
        Role parishRole = role("ADMIN_PARISH");
        assertTrue(resolver.canManageRow(deaneryAdmin, parishRole, permission("parish"), RolePermissionScopeLevel.PARISH, diocese, deanery));
        assertTrue(resolver.canManageRow(deaneryAdmin, parishRole, permission("parish"), RolePermissionScopeLevel.DEANERY, diocese, deanery));
        assertFalse(resolver.canManageRow(deaneryAdmin, parishRole, permission("deanery"), RolePermissionScopeLevel.PARISH, diocese, deanery));
        assertFalse(resolver.canManageRow(deaneryAdmin, parishRole, permission("parish"), RolePermissionScopeLevel.PARISH, diocese, UUID.randomUUID()),
                "giáo hạt khác");
        assertFalse(resolver.canManageRow(deaneryAdmin, parishRole, permission("parish"), RolePermissionScopeLevel.DIOCESE, diocese, null),
                "không đặt ở cấp giáo phận");
        assertFalse(resolver.canManageRow(deaneryAdmin, role("ADMIN_DEANERY"), permission("parish"), RolePermissionScopeLevel.PARISH, diocese, deanery));
    }

    @Test
    void dioceseAdminCannotTouchOtherDioceseOrOwnRoleAndDeaneryRoleStopsAtDeanery() {
        var resolver = resolver();
        Actor dioceseAdmin = new Actor(ActorTier.DIOCESE, diocese, null);
        Role deaneryRole = role("ADMIN_DEANERY");
        assertTrue(resolver.canManageRow(dioceseAdmin, deaneryRole, permission("deanery"), RolePermissionScopeLevel.DEANERY, diocese, deanery));
        assertFalse(resolver.canManageRow(dioceseAdmin, deaneryRole, permission("deanery"), RolePermissionScopeLevel.PARISH, diocese, deanery));
        assertFalse(resolver.canManageRow(dioceseAdmin, deaneryRole, permission("deanery"), RolePermissionScopeLevel.DEANERY, UUID.randomUUID(), deanery));
        assertFalse(resolver.canManageRow(dioceseAdmin, role("ADMIN_DIOCESE"), permission("deanery"), RolePermissionScopeLevel.DIOCESE, diocese, null));
    }
}
