package phucuong.ht.modules.training.crossDeanery.model.entity;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryItemStatus;

@Entity
@Table(name = "cross_deanery_request_item")
@Getter
@Setter
public class CrossDeaneryRequestItem extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private CrossDeaneryRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id")
    private Leader leader;

    // Lí do gửi sang giáo hạt khác (bắt buộc cho từng hồ sơ).
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    // Lí do ngoại lệ khi huynh trưởng chưa đủ điều kiện của khóa đích.
    @Column(name = "exception_reason", length = 500)
    private String exceptionReason;

    // Ghi chú tùy chọn của người gửi, chuyển thành ghi chú hồ sơ khi được đồng ý.
    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 20)
    private CrossDeaneryItemStatus itemStatus;

    // Hồ sơ đăng ký đã sinh ra khi được đồng ý.
    @Column(name = "participation_id")
    private String participationId;
}
