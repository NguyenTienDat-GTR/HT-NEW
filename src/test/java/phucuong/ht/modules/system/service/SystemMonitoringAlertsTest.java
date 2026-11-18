package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Alert;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.IpActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Summary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMonitoringAlertsTest {

    @Test
    void normalTrafficShouldNotRaiseAlerts() {
        assertTrue(SystemMonitoringAlerts.evaluate(summary(200, 1, 0, 300), 180, 0L, List.of()).isEmpty());
    }

    @Test
    void serverErrorsNeedBothCountAndRate() {
        assertEquals(List.of("SERVER_ERRORS"), codes(SystemMonitoringAlerts.evaluate(summary(100, 5, 0, 100), 100, 0L, List.of())));
        assertTrue(SystemMonitoringAlerts.evaluate(summary(1000, 5, 0, 100), 1000, 0L, List.of()).isEmpty());
        assertTrue(SystemMonitoringAlerts.evaluate(summary(10, 4, 0, 100), 10, 0L, List.of()).isEmpty());
    }

    @Test
    void trafficSpikeComparesWithHistoricalAverage() {
        assertEquals(List.of("TRAFFIC_SPIKE"), codes(SystemMonitoringAlerts.evaluate(summary(300, 0, 0, 100), 100, 0L, List.of())));
        assertTrue(SystemMonitoringAlerts.evaluate(summary(299, 0, 0, 100), 10, 0L, List.of()).isEmpty(), "Quá ít request thì không kết luận");
        assertTrue(SystemMonitoringAlerts.evaluate(summary(300, 0, 0, 100), 0, 0L, List.of()).isEmpty(), "Chưa có lịch sử để so sánh");
    }

    @Test
    void slowResponsesAndRateLimitsRaiseWarnings() {
        List<Alert> alerts = SystemMonitoringAlerts.evaluate(summary(30, 0, 0, 3000), 30, 50L, List.of());
        assertEquals(List.of("SLOW_RESPONSES", "RATE_LIMITED"), codes(alerts));
    }

    @Test
    void missingRateLimitCounterIsIgnored() {
        assertTrue(SystemMonitoringAlerts.evaluate(summary(30, 0, 0, 100), 30, null, List.of()).isEmpty());
    }

    @Test
    void failedLoginsEscalateAndComeFirst() {
        List<Alert> alerts = SystemMonitoringAlerts.evaluate(summary(100, 0, 0, 100), 100, 0L,
                List.of(ip("203.0.113.5", 100, 0, 10), ip("203.0.113.6", 100, 40, 30)));

        assertEquals(List.of("LOGIN_ATTACK", "LOGIN_ATTACK", "ACCESS_PROBING"), codes(alerts));
        assertEquals("CRITICAL", alerts.get(0).severity());
        assertTrue(alerts.get(0).title().contains("203.0.113.6"));
        assertEquals("WARNING", alerts.get(1).severity());
    }

    @Test
    void privateAddressesAreNotJudgedByVolume() {
        List<Alert> alerts = SystemMonitoringAlerts.evaluate(summary(100, 0, 0, 100), 100, 0L,
                List.of(ip("172.21.0.4", 5000, 100, 0), ip("203.0.113.9", 1500, 0, 0)));

        assertEquals(List.of("IP_VOLUME"), codes(alerts));
        assertTrue(alerts.get(0).title().contains("203.0.113.9"));
    }

    @Test
    void mostlyPrivateAddressesMeanIpDataIsUnreliable() {
        Summary internal = new Summary(100, 0, 0, 0, 1, 1, 90, 100);
        assertFalse(SystemMonitoringAlerts.ipDataReliable(internal));
        assertEquals(List.of("IP_UNRELIABLE"), codes(SystemMonitoringAlerts.evaluate(internal, 100, 0L, List.of())));
        assertTrue(SystemMonitoringAlerts.ipDataReliable(new Summary(100, 0, 0, 0, 10, 1, 89, 100)));
        assertTrue(SystemMonitoringAlerts.ipDataReliable(new Summary(19, 0, 0, 0, 1, 1, 19, 100)), "Quá ít mẫu thì chưa kết luận");
    }

    private static Summary summary(long total, long serverErrors, long denied, Integer p95) {
        return new Summary(total, serverErrors, denied, 0, 5, 5, 0, p95);
    }

    private static IpActivity ip(String address, long requests, long denied, long failedLogins) {
        return new IpActivity(address, requests, 1, denied + failedLogins, denied, failedLogins, null);
    }

    private static List<String> codes(List<Alert> alerts) {
        return alerts.stream().map(Alert::code).toList();
    }
}
