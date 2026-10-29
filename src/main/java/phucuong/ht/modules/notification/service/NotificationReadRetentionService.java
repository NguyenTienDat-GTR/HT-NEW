package phucuong.ht.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;

import java.time.OffsetDateTime;

/**
 * Thông báo đã đọc tự bị xóa khỏi database sau {@code app.notification-read-retention.days} ngày (mặc định 15)
 * tính từ lúc người dùng đọc. Thông báo chưa đọc không bị đụng tới.
 */
@Service
@Slf4j
public class NotificationReadRetentionService {

    private final TrainingNotificationRepository repository;
    private final int retentionDays;

    public NotificationReadRetentionService(TrainingNotificationRepository repository,
                                            @Value("${app.notification-read-retention.days:15}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public long purgeReadNotifications() {
        return purgeReadNotifications(OffsetDateTime.now());
    }

    @Transactional
    long purgeReadNotifications(OffsetDateTime now) {
        long deleted = repository.deleteByReadAtBefore(now.minusDays(retentionDays));
        if (deleted > 0) {
            log.info("Đã xóa {} thông báo đã đọc quá {} ngày", deleted, retentionDays);
        }
        return deleted;
    }
}
