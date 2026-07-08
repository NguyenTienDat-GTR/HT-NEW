package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.security.permission-expiry-revocation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PermissionExpiryRevocationScheduler {

    private final PermissionExpiryRevocationService permissionExpiryRevocationService;

    @Scheduled(
            fixedDelayString = "${app.security.permission-expiry-revocation.fixed-delay-ms:60000}",
            initialDelayString = "${app.security.permission-expiry-revocation.initial-delay-ms:30000}"
    )
    public void revokeExpiredPermissions() {
        permissionExpiryRevocationService.revokeExpiredPermissions();
    }
}
