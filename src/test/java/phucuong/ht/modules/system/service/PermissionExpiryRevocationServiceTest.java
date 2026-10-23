package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.modules.notification.service.PermissionGrantNotifier;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountPermissionOverride;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import java.util.List;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionExpiryRevocationServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private AccountPermissionOverrideRepository accountPermissionOverrideRepository;

    @Test
    void revokeExpiredPermissionsShouldDeactivateExpiredAssignmentsAndOverrides() {
        PermissionExpiryRevocationService service = new PermissionExpiryRevocationService(
                rolePermissionRepository,
                accountPermissionOverrideRepository
        );
        OffsetDateTime now = OffsetDateTime.parse("2026-07-08T10:15:30+07:00");

        when(rolePermissionRepository.deactivateExpiredAssignments(
                eq(now),
                eq(now),
                eq(PermissionExpiryRevocationService.SYSTEM_ACTOR)
        )).thenReturn(2);
        when(accountPermissionOverrideRepository.deactivateExpiredOverrides(
                eq(now),
                eq(now),
                eq(PermissionExpiryRevocationService.SYSTEM_ACTOR)
        )).thenReturn(3);

        PermissionExpiryRevocationService.PermissionExpiryRevocationSummary summary = service.revokeExpiredPermissions(now);

        assertEquals(2, summary.expiredRolePermissions());
        assertEquals(3, summary.expiredAccountOverrides());
        assertEquals(5, summary.totalRevoked());
        verify(rolePermissionRepository).deactivateExpiredAssignments(now, now, PermissionExpiryRevocationService.SYSTEM_ACTOR);
        verify(accountPermissionOverrideRepository).deactivateExpiredOverrides(now, now, PermissionExpiryRevocationService.SYSTEM_ACTOR);
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setPermissionCode(code);
        return permission;
    }

    @Test
    void expiredPermissionsNotifyEachRoleAndEachAccountOnceWithTheirOwnPermissions() {
        PermissionGrantNotifier notifier = org.mockito.Mockito.mock(PermissionGrantNotifier.class);
        PermissionExpiryRevocationService service = new PermissionExpiryRevocationService(rolePermissionRepository, accountPermissionOverrideRepository);
        ReflectionTestUtils.setField(service, "permissionGrantNotifier", notifier);
        OffsetDateTime now = OffsetDateTime.parse("2026-07-08T10:15:30+07:00");

        Role adminRole = new Role();
        adminRole.setRoleCode("ADMIN_DIOCESE");
        Role otherRole = new Role();
        otherRole.setRoleCode("ADMIN_DEANERY");
        RolePermission first = new RolePermission();
        first.setRole(adminRole);
        first.setPermission(permission("a"));
        RolePermission second = new RolePermission();
        second.setRole(adminRole);
        second.setPermission(permission("b"));
        RolePermission third = new RolePermission();
        third.setRole(otherRole);
        third.setPermission(permission("c"));
        Account an = new Account();
        an.setUsername("an");
        AccountPermissionOverride override = new AccountPermissionOverride();
        override.setAccount(an);
        override.setPermission(permission("d"));

        when(rolePermissionRepository.findExpiredAssignments(now)).thenReturn(List.of(first, second, third));
        when(accountPermissionOverrideRepository.findExpiredOverrides(now)).thenReturn(List.of(override));
        when(rolePermissionRepository.deactivateExpiredAssignments(eq(now), eq(now), eq(PermissionExpiryRevocationService.SYSTEM_ACTOR))).thenReturn(3);
        when(accountPermissionOverrideRepository.deactivateExpiredOverrides(eq(now), eq(now), eq(PermissionExpiryRevocationService.SYSTEM_ACTOR))).thenReturn(1);

        service.revokeExpiredPermissions(now);

        verify(notifier).notifyRoleExpired(adminRole, List.of(first.getPermission(), second.getPermission()));
        verify(notifier).notifyRoleExpired(otherRole, List.of(third.getPermission()));
        verify(notifier).notifyAccountExpired("an", List.of(override.getPermission()));
    }

    @Test
    void nothingIsLookedUpOrNotifiedWhenNoNotifierIsAvailable() {
        PermissionExpiryRevocationService service = new PermissionExpiryRevocationService(rolePermissionRepository, accountPermissionOverrideRepository);
        OffsetDateTime now = OffsetDateTime.parse("2026-07-08T10:15:30+07:00");
        service.revokeExpiredPermissions(now);
        verify(rolePermissionRepository, never()).findExpiredAssignments(now);
        verify(accountPermissionOverrideRepository, never()).findExpiredOverrides(now);
    }
}
