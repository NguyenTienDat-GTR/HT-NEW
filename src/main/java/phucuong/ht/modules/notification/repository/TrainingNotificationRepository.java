package phucuong.ht.modules.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TrainingNotificationRepository extends JpaRepository<TrainingNotification, String> {

    List<TrainingNotification> findAllByUsernameAndStatusTrueOrderByCreatedAtDesc(String username);

    Page<TrainingNotification> findAllByUsernameAndStatusTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String username,
            OffsetDateTime createdAt,
            Pageable pageable
    );

    Optional<TrainingNotification> findByIdAndUsernameAndStatusTrueAndCreatedAtGreaterThanEqual(
            String id,
            String username,
            OffsetDateTime createdAt
    );

    long countByUsernameAndStatusTrueAndReadAtIsNull(String username);

    long countByUsernameAndStatusTrueAndReadAtIsNullAndCreatedAtGreaterThanEqual(String username, OffsetDateTime createdAt);

    long deleteByCreatedAtBefore(OffsetDateTime createdAt);

    /** Xóa thông báo đã đọc từ trước mốc {@code readAt} (thông báo chưa đọc có read_at null nên không bị xóa). */
    long deleteByReadAtBefore(OffsetDateTime readAt);
}
