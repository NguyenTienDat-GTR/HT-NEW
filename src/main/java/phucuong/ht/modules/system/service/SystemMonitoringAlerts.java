package phucuong.ht.modules.system.service;

import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Alert;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.IpActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Summary;
import phucuong.ht.modules.system.repository.SystemMonitoringRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Quy tắc phát hiện bất thường trên số liệu 60 phút gần nhất. Hàm thuần, không truy cập dữ liệu. */
final class SystemMonitoringAlerts {

    static final int FAILED_LOGIN_THRESHOLD = 10;
    static final int FAILED_LOGIN_CRITICAL_THRESHOLD = 30;
    static final int DENIED_THRESHOLD = 30;
    static final int IP_REQUEST_THRESHOLD = 1500;
    static final int MIN_SERVER_ERRORS = 5;
    static final double SERVER_ERROR_RATE = 0.05;
    static final int MIN_SPIKE_REQUESTS = 300;
    static final double SPIKE_FACTOR = 3;
    static final int SLOW_P95_MS = 3000;
    static final int MIN_SLOW_REQUESTS = 30;
    static final int RATE_LIMITED_THRESHOLD = 50;
    static final int MIN_IP_SAMPLE = 20;
    static final double PRIVATE_IP_SHARE = 0.9;

    private static final Pattern PRIVATE_IP = Pattern.compile(SystemMonitoringRepository.PRIVATE_IP_REGEX);
    private static final Map<String, Integer> SEVERITY_ORDER = Map.of("CRITICAL", 0, "WARNING", 1, "INFO", 2);

    private SystemMonitoringAlerts() {
    }

    static boolean ipDataReliable(Summary summary) {
        return summary.totalRequests() < MIN_IP_SAMPLE
                || summary.privateIpRequests() < summary.totalRequests() * PRIVATE_IP_SHARE;
    }

    static List<Alert> evaluate(Summary lastHour, double averagePerHour, Long rateLimited, List<IpActivity> suspiciousIps) {
        List<Alert> alerts = new ArrayList<>();
        long total = lastHour.totalRequests();

        if (lastHour.serverErrors() >= MIN_SERVER_ERRORS && lastHour.serverErrors() >= total * SERVER_ERROR_RATE) {
            alerts.add(new Alert("SERVER_ERRORS", "CRITICAL", "Tỉ lệ lỗi hệ thống cao",
                    format("%d trong %d request gặp lỗi 5xx (%.1f%%) trong 60 phút qua.",
                            lastHour.serverErrors(), total, lastHour.serverErrors() * 100.0 / total)));
        }
        if (total >= MIN_SPIKE_REQUESTS && averagePerHour > 0 && total >= averagePerHour * SPIKE_FACTOR) {
            alerts.add(new Alert("TRAFFIC_SPIKE", "WARNING", "Lưu lượng tăng đột biến",
                    format("%d request trong 60 phút qua, gấp %.1f lần mức trung bình %.0f request mỗi giờ của 7 ngày trước.",
                            total, total / averagePerHour, averagePerHour)));
        }
        if (lastHour.p95Ms() != null && lastHour.p95Ms() >= SLOW_P95_MS && total >= MIN_SLOW_REQUESTS) {
            alerts.add(new Alert("SLOW_RESPONSES", "WARNING", "Hệ thống phản hồi chậm",
                    format("95%% request trong 60 phút qua xử lý xong trong %d ms; ngưỡng cảnh báo là %d ms.",
                            lastHour.p95Ms(), SLOW_P95_MS)));
        }
        if (rateLimited != null && rateLimited >= RATE_LIMITED_THRESHOLD) {
            alerts.add(new Alert("RATE_LIMITED", "WARNING", "Nhiều request bị giới hạn tần suất",
                    format("%d request bị chặn với mã 429 trong giờ này và giờ trước.", rateLimited)));
        }
        for (IpActivity ip : suspiciousIps) {
            addIpAlerts(alerts, ip);
        }
        if (!ipDataReliable(lastHour)) {
            alerts.add(new Alert("IP_UNRELIABLE", "INFO", "Chưa nhận được IP thật của người dùng",
                    "Phần lớn request ghi nhận địa chỉ nội bộ, nên số liệu theo IP chưa phản ánh đúng từng người dùng."));
        }
        alerts.sort(Comparator.comparingInt(alert -> SEVERITY_ORDER.get(alert.severity())));
        return List.copyOf(alerts);
    }

    private static void addIpAlerts(List<Alert> alerts, IpActivity ip) {
        String address = ip.ipAddress();
        if (ip.failedLogins() >= FAILED_LOGIN_THRESHOLD) {
            alerts.add(new Alert("LOGIN_ATTACK", ip.failedLogins() >= FAILED_LOGIN_CRITICAL_THRESHOLD ? "CRITICAL" : "WARNING",
                    "Nghi dò mật khẩu từ " + address,
                    format("%d lần đăng nhập thất bại trong 60 phút qua.", ip.failedLogins())));
        }
        // Địa chỉ nội bộ có thể là proxy gom nhiều người dùng, nên không kết luận theo số lượng.
        if (PRIVATE_IP.matcher(address).find()) {
            return;
        }
        if (ip.deniedRequests() >= DENIED_THRESHOLD) {
            alerts.add(new Alert("ACCESS_PROBING", "WARNING", "Nhiều request bị từ chối từ " + address,
                    format("%d request nhận mã 401 hoặc 403 trong 60 phút qua.", ip.deniedRequests())));
        }
        if (ip.requests() >= IP_REQUEST_THRESHOLD) {
            alerts.add(new Alert("IP_VOLUME", "WARNING", "Một IP gọi quá nhiều: " + address,
                    format("%d request trong 60 phút qua.", ip.requests())));
        }
    }

    private static String format(String template, Object... values) {
        return String.format(Locale.forLanguageTag("vi-VN"), template, values);
    }
}
