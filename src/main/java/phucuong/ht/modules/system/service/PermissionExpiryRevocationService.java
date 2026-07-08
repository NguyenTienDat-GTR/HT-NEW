package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionExpiryRevocationService {

    static final String SYSTEM_ACTOR = "system:permission-expiry-revocation";

    private final RolePermissionRepository rolePermissionRepository;
    private final AccountPermissionOverrideRepository accountPermissionOverrideRepository;

    @Transactional
    public PermissionExpiryRevocationSummary revokeExpiredPermissions() {
        return revokeExpiredPermissions(OffsetDateTime.now());
    }

    @Transactional
    PermissionExpiryRevocationSummary revokeExpiredPermissions(OffsetDateTime now) {
        int expiredRolePermissions = rolePermissionRepository.deactivateExpiredAssignments(now, now, SYSTEM_ACTOR);
        int expiredAccountOverrides = accountPermissionOverrideRepository.deactivateExpiredOverrides(now, now, SYSTEM_ACTOR);
        int totalRevoked = expiredRolePermissions + expiredAccountOverrides;

        if (totalRevoked > 0) {
            log.info(
                    "Automatically revoked {} expired permission records (rolePermissions={}, accountOverrides={})",
                    totalRevoked,
                    expiredRolePermissions,
                    expiredAccountOverrides
            );
        }

        return new PermissionExpiryRevocationSummary(expiredRolePermissions, expiredAccountOverrides);
    }

    public record PermissionExpiryRevocationSummary(
            int expiredRolePermissions,
            int expiredAccountOverrides
    ) {
        public int totalRevoked() {
            return expiredRolePermissions + expiredAccountOverrides;
        }
    }
}
