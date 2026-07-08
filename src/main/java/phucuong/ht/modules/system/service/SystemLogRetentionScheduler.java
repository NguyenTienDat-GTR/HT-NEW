package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.system-log-retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SystemLogRetentionScheduler {

    private final SystemLogRetentionService systemLogRetentionService;

    @Scheduled(
            fixedDelayString = "${app.system-log-retention.fixed-delay-ms:86400000}",
            initialDelayString = "${app.system-log-retention.initial-delay-ms:60000}"
    )
    public void purgeExpiredLogs() {
        systemLogRetentionService.purgeExpiredLogs();
    }
}
