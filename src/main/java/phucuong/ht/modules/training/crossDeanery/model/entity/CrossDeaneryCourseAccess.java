package phucuong.ht.modules.training.crossDeanery.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryAccessDecision;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Quyết định của một giáo hạt (deanery) về việc cho giáo xứ của mình gửi hồ sơ sang khóa giáo hạt đang mở liên giáo hạt.
 * Mỗi (khóa, giáo hạt) tối đa một dòng; chưa có dòng nghĩa là giáo hạt chưa quyết định nên giáo xứ chưa gửi được.
 */
@Entity
@Table(name = "cross_deanery_course_access")
@Getter
@Setter
public class CrossDeaneryCourseAccess extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(name = "deanery_id", nullable = false)
    private UUID deaneryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private CrossDeaneryAccessDecision decision;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
