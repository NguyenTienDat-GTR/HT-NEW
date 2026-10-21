package phucuong.ht.modules.rankHistory.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;

import java.time.LocalDate;

@Entity
@Table(name = "rank_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class RankHistory extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "leader_id")
    private Leader leader;

    private String oldLevel;

    private String newLevel;

    private LocalDate promotionDate;

    private String note;

    // Khóa sinh ra lần thăng cấp này (null với lịch sử nhập tay hoặc dữ liệu cũ không xác định được).
    @Column(name = "course_code")
    private String courseCode;

    // Đã hạ cấp lại khi chứng nhận của khóa bị thu hồi.
    @Column(name = "reverted", nullable = false)
    private Boolean reverted = Boolean.FALSE;
}
