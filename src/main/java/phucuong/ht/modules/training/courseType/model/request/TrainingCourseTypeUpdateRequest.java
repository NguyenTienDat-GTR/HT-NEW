package phucuong.ht.modules.training.courseType.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

@Getter
@Setter
public class TrainingCourseTypeUpdateRequest {

    private String typeName;

    private UnitType managementUnitType;

    private String requiredCurrentLevel;

    // 0 = không yêu cầu tuổi tối thiểu (lưu thành trống).
    private Integer requiredMinAge;

    @jakarta.validation.constraints.Min(1)
    private Integer maxYearsAfterPrerequisite;

    // true = đặt về không giới hạn (xóa giá trị maxYearsAfterPrerequisite).
    private Boolean clearMaxYearsAfterPrerequisite;

    private String prerequisiteTypeCode;

    private String resultingLevel;

    // true = bỏ trống cấp độ khi hoàn thành (hoàn thành khóa không thăng cấp nữa); ưu tiên hơn resultingLevel.
    private Boolean clearResultingLevel;

    private Boolean hasCertificate;

    private String certificateShortCode;

    // Cho phép gỡ khóa tiên quyết khi gửi true.
    private Boolean clearPrerequisite;
}
