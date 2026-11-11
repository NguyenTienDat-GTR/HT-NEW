package phucuong.ht.modules.training.courseType.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

@Getter
@Setter
public class TrainingCourseTypeCreateRequest {

    // Mã loại khóa do hệ thống tự sinh (UUID); client không gửi.

    @NotBlank
    private String typeName;

    @NotNull
    private UnitType managementUnitType;

    private String requiredCurrentLevel;

    // 0 = không yêu cầu tuổi tối thiểu (lưu thành trống).
    private Integer requiredMinAge;

    @jakarta.validation.constraints.Min(1)
    private Integer maxYearsAfterPrerequisite;

    private String prerequisiteTypeCode;

    private String resultingLevel;

    private Boolean hasCertificate;

    private String certificateShortCode;
}
