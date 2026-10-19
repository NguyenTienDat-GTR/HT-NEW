package phucuong.ht.modules.training.trainingCourse.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "training_course")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class TrainingCourse extends BaseModel {

    @Id
    @Column(name = "course_code")
    private String courseCode;

    private String courseName;

    private Integer hostYear;

    private String location;

    private LocalDate startDate;

    private LocalDate endDate;

    private OffsetDateTime registrationStartAt;

    private OffsetDateTime registrationEndAt;

    // Loại khóa (catalog quản lý được), đã bao gồm điều kiện tham dự.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_type_code", nullable = false)
    private TrainingCourseType courseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;

    // Điểm tổng kết tối thiểu để đạt khóa.
    @Column(name = "passing_score", nullable = false)
    private BigDecimal passingScore;

    private String additionalNotes;
}
