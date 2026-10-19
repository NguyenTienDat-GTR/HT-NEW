package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class TrainingCourseResponse {
    private String courseCode;
    private String courseName;
    private String courseTypeCode;
    private String courseTypeName;
    private UnitType managementUnitType;
    private Integer hostYear;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private OffsetDateTime registrationStartAt;
    private OffsetDateTime registrationEndAt;
    private UUID dioceseId;
    private UUID deaneryId;
    private String scoreFormulaId;
    private String additionalNotes;
    private Boolean status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
