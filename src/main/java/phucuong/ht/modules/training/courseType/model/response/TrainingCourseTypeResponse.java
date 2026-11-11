package phucuong.ht.modules.training.courseType.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.OffsetDateTime;

@Getter
@Builder(toBuilder = true)
public class TrainingCourseTypeResponse {
    private String typeCode;
    private java.util.UUID dioceseId;
    private String typeName;
    private UnitType managementUnitType;
    // Chủ sở hữu: DIOCESE = giáo phận tạo (loại cấp giáo hạt thì dùng chung cho mọi giáo hạt), DEANERY = giáo hạt ownerDeaneryId tự tạo.
    private UnitType ownerUnitType;
    private java.util.UUID ownerDeaneryId;
    // Tài khoản hiện tại có sửa / đổi trạng thái được loại khóa này không (theo chủ sở hữu; quyền chức năng kiểm tra riêng).
    private Boolean editable;
    private String requiredCurrentLevel;
    private Integer requiredMinAge;
    private Integer maxYearsAfterPrerequisite;
    private String prerequisiteTypeCode;
    private String prerequisiteTypeName;
    private UnitType prerequisiteManagementUnitType;
    // Khác null: khóa tiên quyết do giáo hạt này tự tạo (không dùng chung); null: của giáo phận.
    private java.util.UUID prerequisiteOwnerDeaneryId;
    private String resultingLevel;
    private String certificateShortCode;
    private Boolean status;
    private String createdBy;
    private String createdByName;
    private String updatedBy;
    private String updatedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
