package phucuong.ht.modules.training.courseScoreComponent.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.math.BigDecimal;

// Thành phần điểm thuộc riêng một khóa huấn luyện: tên, điểm tối đa, hệ số, cờ tính vào điểm tổng kết và thứ tự.
// Khai báo ngay khi tạo/sửa khóa, không dùng chung giữa các khóa.
@Entity
@Table(name = "training_course_score_component")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"course"})
@EqualsAndHashCode(callSuper = true)
public class TrainingCourseScoreComponent extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_code")
    private TrainingCourse course;

    private String name;

    private String description;

    @Column(name = "max_score")
    private BigDecimal maxScore;

    // Hệ số khi tính điểm tổng kết (trung bình có hệ số).
    @Column(name = "coefficient")
    private BigDecimal coefficient;

    // Có tính thành phần này vào điểm tổng kết hay không.
    @Column(name = "include_in_final")
    private Boolean includeInFinal;

    @Column(name = "display_order")
    private Integer displayOrder;
}
