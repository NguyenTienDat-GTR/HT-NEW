package phucuong.ht.modules.training.courseParticipation.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class CourseParticipationUpdateRequest {

    private UUID leaderId;

    private String courseCode;

    private LocalDate registrationDate;

    private ParticipationStatus participationStatus;

    private BigDecimal totalScore;

    private CourseResult result;

    private String review;
}
