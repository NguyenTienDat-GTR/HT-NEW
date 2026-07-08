package phucuong.ht.modules.notification.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "training_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TrainingNotification extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_unit_type")
    private UnitType senderUnitType;

    @Column(name = "sender_unit_id")
    private UUID senderUnitId;

    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "approved_count")
    private Integer approvedCount;

    @Column(name = "pending_count")
    private Integer pendingCount;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
