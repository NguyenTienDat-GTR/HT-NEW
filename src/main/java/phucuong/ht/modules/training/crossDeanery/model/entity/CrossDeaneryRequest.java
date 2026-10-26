package phucuong.ht.modules.training.crossDeanery.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu gửi hồ sơ sang khóa giáo hạt của giáo hạt khác. Giáo xứ gửi thì chờ giáo hạt nhà (home) đồng ý; giáo hạt nhà gửi thẳng
 * thì được coi là đã đồng ý. Chỉ khi được đồng ý mới sinh hồ sơ đăng ký chờ duyệt bình thường ở khóa đích.
 */
@Entity
@Table(name = "cross_deanery_request")
@Getter
@Setter
public class CrossDeaneryRequest extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_code")
    private TrainingCourse course;

    // Giáo hạt nhà của huynh trưởng: người có quyền đồng ý thuộc giáo hạt này.
    @Column(name = "home_deanery_id", nullable = false)
    private UUID homeDeaneryId;

    // Giáo xứ gửi (null khi giáo hạt nhà gửi thẳng).
    @Column(name = "parish_id")
    private UUID parishId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 20)
    private UnitType requesterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 20)
    private CrossDeaneryStatus requestStatus;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrossDeaneryRequestItem> items = new ArrayList<>();
}
