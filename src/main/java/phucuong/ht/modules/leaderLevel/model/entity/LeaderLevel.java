package phucuong.ht.modules.leaderLevel.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import phucuong.ht.common.model.BaseModel;

import java.util.UUID;

// Một cấp độ huynh trưởng trong danh mục của một giáo phận. displayOrder càng lớn thì cấp càng cao;
// cấp đứng ngay trước trong thứ tự là điều kiện để đạt cấp này.
@Entity
@Table(name = "leader_level")
@IdClass(LeaderLevelId.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class LeaderLevel extends BaseModel {

    // Giáo phận sở hữu danh mục.
    @Id
    @Column(name = "diocese_id")
    private UUID dioceseId;

    // Mã cấp độ cũng là giá trị lưu ở leader, loại khóa, lịch sử cấp bậc (ví dụ HT_I).
    @Id
    @Column(name = "level_code")
    private String levelCode;

    @Column(name = "level_name", nullable = false)
    private String levelName;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    // Cấp hệ thống (NONE - chưa có cấp): luôn thấp nhất, không xóa, không đổi thứ tự.
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = Boolean.FALSE;

    @Column(name = "description")
    private String description;
}
