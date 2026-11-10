package phucuong.ht.modules.training.courseType.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;

import java.util.UUID;

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

    // Danh mục loại khóa đi riêng theo từng giáo phận.
    @Column(name = "diocese_id", nullable = false)
    private UUID dioceseId;

    @Column(name = "type_name")
    private String typeName;

    // Cấp đơn vị quản lý khóa thuộc loại này: DIOCESE (giáo phận) hoặc DEANERY (giáo hạt - khóa DT).
    @Enumerated(EnumType.STRING)
    @Column(name = "management_unit_type")
    private UnitType managementUnitType;

    // Điều kiện tham dự (gộp từ course_requirement cũ).
    @Column(name = "required_current_level")
    private String requiredCurrentLevel;

    @Column(name = "required_min_age")
    private Integer requiredMinAge;

    // Phải đi khóa này trong vòng bao nhiêu năm kể từ lần đạt khóa tiên quyết gần nhất (năm tổ chức khóa - năm đạt <= giá trị này).
    @Column(name = "max_years_after_prerequisite")
    private Integer maxYearsAfterPrerequisite;

    // Khóa tiên quyết: tham chiếu tới chính loại khóa khác.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_type_code")
    private TrainingCourseType prerequisiteType;

    // Cấp bậc được thăng khi hoàn thành khóa thuộc loại này (có cấp chứng nhận thì theo chứng nhận được duyệt, không thì theo kết quả khóa).
    @Column(name = "resulting_level")
    private String resultingLevel;

    // Khóa thuộc loại này có cấp chứng nhận (chỉ lo việc cấp chứng nhận, không liên quan thăng cấp).
    @Column(name = "has_certificate", nullable = false)
    private boolean hasCertificate;

    // Mã viết tắt dùng khi sinh mã chứng chỉ.
    @Column(name = "certificate_short_code")
    private String certificateShortCode;
}
