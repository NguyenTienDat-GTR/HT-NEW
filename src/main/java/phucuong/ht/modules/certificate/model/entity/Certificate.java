package phucuong.ht.modules.certificate.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;

@Entity
@Table(name = "certificate")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Certificate extends BaseModel {

    @Id
    @Column(name = "certificate_code")
    private String certificateCode;

    private String oldCertificateCode;

    private String certificateName;

    @ManyToOne
    @JoinColumn(name = "leader_id")
    private Leader leader;

    @ManyToOne
    @JoinColumn(name = "course_code")
    private TrainingCourse course;

    private String result;

    private String signedBy;

    private LocalDate issueDate;

    private String imageUrl;
}
