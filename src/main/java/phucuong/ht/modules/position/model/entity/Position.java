package phucuong.ht.modules.position.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "position")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Position extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "position_code", unique = true)
    private String positionCode;

    @Column(name = "position_name", nullable = false)
    private String positionName;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;

    @Column(name = "position_type")
    @Enumerated(EnumType.STRING)
    private UnitType positionType;

    /** Giáo phận sở hữu chức vụ: mỗi giáo phận có danh sách chức vụ riêng. */
    @Column(name = "diocese_id", nullable = false, updatable = false)
    private java.util.UUID dioceseId;
}