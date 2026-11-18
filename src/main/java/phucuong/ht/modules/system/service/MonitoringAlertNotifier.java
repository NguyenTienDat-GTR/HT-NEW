package phucuong.ht.modules.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Alert;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gửi cảnh báo bất thường cho superadmin qua email. Mỗi cảnh báo (cùng loại, cùng nội dung, cùng mức độ) chỉ gửi một lần
 * trong khoảng chờ để tình trạng kéo dài không làm đầy hộp thư; mức độ tăng lên thì gửi lại ngay.
 */
@Slf4j
@Service
public class MonitoringAlertNotifier {

    static final String SENT_KEY_PREFIX = "monitoring:alert-sent:";
    /** Thông tin (INFO) không đáng làm phiền: chỉ hiển thị trên trang giám sát. */
    private static final Set<String> NOTIFIED_SEVERITIES = Set.of("CRITICAL", "WARNING");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final SystemMonitoringService monitoringService;
    private final SuperAdminEmailResolver recipients;
    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;
    private final Duration cooldown;
    private final String from;
    private final String frontendBaseUrl;

    public MonitoringAlertNotifier(SystemMonitoringService monitoringService,
                                   SuperAdminEmailResolver recipients,
                                   StringRedisTemplate redis,
                                   JavaMailSender mailSender,
                                   @Value("${app.monitoring-alerts.cooldown-minutes:360}") long cooldownMinutes,
                                   @Value("${app.mail.from:${spring.mail.username:}}") String from,
                                   @Value("${app.frontend.base-url:}") String frontendBaseUrl) {
        this.monitoringService = monitoringService;
        this.recipients = recipients;
        this.redis = redis;
        this.mailSender = mailSender;
        this.cooldown = Duration.ofMinutes(Math.max(1, cooldownMinutes));
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Trả về số cảnh báo mới đã gửi. Không ném lỗi: một lần thất bại không được làm hỏng lần chạy sau. */
    public int notifyNewAlerts() {
        try {
            return doNotify();
        } catch (RuntimeException exception) {
            log.warn("Không gửi được email cảnh báo giám sát hệ thống", exception);
            return 0;
        }
    }

    private int doNotify() {
        List<Alert> candidates = monitoringService.recentAlerts().stream()
                .filter(alert -> NOTIFIED_SEVERITIES.contains(alert.severity()))
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }
        List<String> to = recipients.emails();
        if (to.isEmpty()) {
            // Chưa đánh dấu đã gửi để khi superadmin có email thì cảnh báo đang diễn ra vẫn được báo.
            log.warn("Có {} cảnh báo giám sát hệ thống nhưng không tài khoản superadmin nào có email để nhận", candidates.size());
            return 0;
        }

        List<Alert> fresh = new ArrayList<>();
        List<String> claimedKeys = new ArrayList<>();
        for (Alert alert : candidates) {
            String key = key(alert);
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", cooldown))) {
                fresh.add(alert);
                claimedKeys.add(key);
            }
        }
        if (fresh.isEmpty()) {
            return 0;
        }

        int delivered = 0;
        for (String recipient : to) {
            try {
                mailSender.send(message(recipient, fresh));
                delivered++;
            } catch (RuntimeException exception) {
                log.warn("Không gửi được email cảnh báo giám sát tới {}", recipient, exception);
            }
        }
        if (delivered == 0) {
            // Không ai nhận được: bỏ đánh dấu để lần chạy sau thử lại.
            redis.delete(claimedKeys);
            return 0;
        }
        return fresh.size();
    }

    static String key(Alert alert) {
        return SENT_KEY_PREFIX + alert.severity() + ":" + alert.code() + ":" + alert.title();
    }

    SimpleMailMessage message(String recipient, List<Alert> alerts) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(recipient);
        boolean critical = alerts.stream().anyMatch(alert -> "CRITICAL".equals(alert.severity()));
        message.setSubject((critical ? "[Nghiêm trọng] " : "[Cần chú ý] ") + "Cảnh báo hệ thống quản lí Huynh Trưởng ("
                + alerts.size() + " cảnh báo mới)");
        message.setText(body(alerts));
        return message;
    }

    String body(List<Alert> alerts) {
        StringBuilder text = new StringBuilder("Xin chào,\n\nHệ thống phát hiện dấu hiệu bất thường lúc ")
                .append(ZonedDateTime.now(VIETNAM).format(TIME_FORMAT)).append(":\n\n");
        for (Alert alert : alerts) {
            text.append("- ").append("CRITICAL".equals(alert.severity()) ? "[Nghiêm trọng] " : "[Cần chú ý] ")
                    .append(alert.title()).append('\n')
                    .append("  ").append(alert.detail()).append("\n\n");
        }
        text.append("Xem chi tiết tại Hệ thống → Giám sát hệ thống");
        if (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
            text.append(": ").append(frontendBaseUrl.replaceAll("/+$", "")).append("/system/monitoring");
        }
        text.append(".\n\nMỗi cảnh báo chỉ được gửi một lần trong ").append(cooldown.toMinutes() >= 60
                        ? cooldown.toHours() + " giờ" : cooldown.toMinutes() + " phút")
                .append(" khi tình trạng còn kéo dài. Đây là email tự động, vui lòng không trả lời.");
        return text.toString();
    }
}
