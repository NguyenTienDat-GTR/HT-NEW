package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.config.RedisRateLimitService;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Alert;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.IpActivity;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse.Summary;
import phucuong.ht.modules.system.repository.SystemMonitoringRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemMonitoringService {

    /** Khoảng thời gian được phép xem (giờ) -> độ rộng mỗi mốc trên biểu đồ (phút). */
    private static final Map<Integer, Integer> BUCKET_MINUTES = Map.of(1, 5, 6, 15, 24, 60, 72, 180, 168, 360, 240, 720);
    private static final int TOP_LIMIT = 15;
    private static final int MIN_ENDPOINT_SAMPLE = 5;
    private static final int SUSPICIOUS_IP_LIMIT = 10;

    private final SystemMonitoringRepository repository;
    private final RedisRateLimitService rateLimitService;

    @Transactional(readOnly = true, timeout = 20)
    public SystemMonitoringResponse overview(int hours) {
        Integer bucketMinutes = BUCKET_MINUTES.get(hours);
        if (bucketMinutes == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Khoảng thời gian giám sát không hợp lệ");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = now.minusHours(hours);
        Summary summary = repository.summary(from, now);
        // Kỳ liền trước vượt quá thời gian lưu nhật ký thì số liệu thiếu, so sánh sẽ gây hiểu nhầm.
        Summary previous = hours * 2 <= SystemLogRetentionService.RETENTION_DAYS * 24
                ? repository.summary(from.minusHours(hours), from)
                : null;

        return new SystemMonitoringResponse(
                hours,
                bucketMinutes,
                from,
                now,
                summary,
                previous,
                rateLimited(from, now),
                SystemMonitoringAlerts.ipDataReliable(summary),
                repository.timeline(from, now, bucketMinutes),
                repository.topIps(from, now, TOP_LIMIT),
                repository.topAccounts(from, now, TOP_LIMIT),
                repository.busiestEndpoints(from, now, TOP_LIMIT),
                repository.slowestEndpoints(from, now, MIN_ENDPOINT_SAMPLE, TOP_LIMIT),
                alertsAt(now)
        );
    }

    /** Cảnh báo bất thường trong 60 phút gần nhất; dùng cho cả trang giám sát và email gửi superadmin. */
    @Transactional(readOnly = true, timeout = 20)
    public List<Alert> recentAlerts() {
        return alertsAt(OffsetDateTime.now());
    }

    private List<Alert> alertsAt(OffsetDateTime now) {
        OffsetDateTime lastHourStart = now.minusHours(1);
        Summary lastHour = repository.summary(lastHourStart, now);
        double averagePerHour = repository.averageRequestsPerActiveHour(lastHourStart.minusDays(7), lastHourStart);
        List<IpActivity> suspiciousIps = repository.suspiciousIps(lastHourStart, now,
                SystemMonitoringAlerts.FAILED_LOGIN_THRESHOLD, SystemMonitoringAlerts.DENIED_THRESHOLD,
                SystemMonitoringAlerts.IP_REQUEST_THRESHOLD, SUSPICIOUS_IP_LIMIT);
        return SystemMonitoringAlerts.evaluate(lastHour, averagePerHour, rateLimited(lastHourStart, now), suspiciousIps);
    }

    /** Bộ đếm 429 chỉ là số liệu phụ: Redis lỗi thì trang vẫn hiển thị phần còn lại. Đếm theo giờ tròn. */
    private Long rateLimited(OffsetDateTime from, OffsetDateTime to) {
        try {
            return rateLimitService.countRejected(from.truncatedTo(ChronoUnit.HOURS).toInstant(), to.toInstant());
        } catch (RuntimeException exception) {
            log.warn("Không đọc được bộ đếm request bị giới hạn tần suất", exception);
            return null;
        }
    }
}
