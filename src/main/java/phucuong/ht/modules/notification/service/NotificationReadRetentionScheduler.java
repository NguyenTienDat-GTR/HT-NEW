package phucuong.ht.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification-read-retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationReadRetentionScheduler {

    private final NotificationReadRetentionService retentionService;

    @Scheduled(
            fixedDelayString = "${app.notification-read-retention.fixed-delay-ms:3600000}",
            initialDelayString = "${app.notification-read-retention.initial-delay-ms:90000}"
    )
    public void purgeReadNotifications() {
        retentionService.purgeReadNotifications();
    }
}
