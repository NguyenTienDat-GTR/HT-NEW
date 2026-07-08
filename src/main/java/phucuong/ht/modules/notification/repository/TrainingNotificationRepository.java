package phucuong.ht.modules.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;

import java.util.List;
import java.util.Optional;

public interface TrainingNotificationRepository extends JpaRepository<TrainingNotification, String> {

    List<TrainingNotification> findAllByUsernameAndStatusTrueOrderByCreatedAtDesc(String username);

    Optional<TrainingNotification> findByIdAndUsernameAndStatusTrue(String id, String username);
}
