package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
}
