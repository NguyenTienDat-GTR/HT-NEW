package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.monitoring-alerts",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MonitoringAlertScheduler {

    private final MonitoringAlertNotifier notifier;

    @Scheduled(
            fixedDelayString = "${app.monitoring-alerts.fixed-delay-ms:600000}",
            initialDelayString = "${app.monitoring-alerts.initial-delay-ms:180000}"
    )
    public void notifyNewAlerts() {
        notifier.notifyNewAlerts();
    }
}
