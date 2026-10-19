package phucuong.ht.modules.training.courseType.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

@Entity
@Table(name = "training_course_type")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class TrainingCourseType extends BaseModel {

    @Id
    @Column(name = "type_code")
    private String typeCode;

    @Column(name = "type_name")
    private String typeName;

    // Cấp đơn vị quản lý khóa thuộc loại này: DIOCESE (giáo phận) hoặc DEANERY (giáo hạt - khóa DT).
    @Enumerated(EnumType.STRING)
    @Column(name = "management_unit_type")
    private UnitType managementUnitType;

    // Điều kiện tham dự (gộp từ course_requirement cũ).
    @Enumerated(EnumType.STRING)
    @Column(name = "required_current_level")
    private LeaderLevel requiredCurrentLevel;

    @Column(name = "required_min_age")
    private Integer requiredMinAge;

    @Column(name = "required_years_active")
    private Integer requiredYearsActive;

    // Khóa tiên quyết: tham chiếu tới chính loại khóa khác.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_type_code")
    private TrainingCourseType prerequisiteType;

    // Cấp bậc được thăng khi chứng chỉ của khóa thuộc loại này được duyệt.
    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_level")
    private LeaderLevel resultingLevel;

    // Mã viết tắt dùng khi sinh mã chứng chỉ.
    @Column(name = "certificate_short_code")
    private String certificateShortCode;
}
