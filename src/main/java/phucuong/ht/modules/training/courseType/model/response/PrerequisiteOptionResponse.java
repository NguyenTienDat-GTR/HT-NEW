package phucuong.ht.modules.training.courseType.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

/** Một loại khóa có thể chọn làm khóa tiên quyết, kèm thông tin giúp người dùng phân biệt khi chọn. */
@Getter
@Builder
public class PrerequisiteOptionResponse {
    private String typeCode;
    private String typeName;
    private UnitType managementUnitType;
    // DIOCESE: loại khóa của giáo phận (cấp giáo hạt thì dùng chung cho mọi giáo hạt); DEANERY: do giáo hạt tự tạo.
    private UnitType ownerUnitType;
    private Boolean status;
    private String requiredCurrentLevel;
    private String requiredCurrentLevelName;
    private String resultingLevel;
    private String resultingLevelName;
    // Khóa tiên quyết của chính loại khóa này (để thấy chuỗi khóa).
    private String prerequisiteTypeName;
    // Số khóa đã tạo thuộc loại này (mọi giáo hạt / giáo phận).
    private long courseCount;
    // Số huynh trưởng đã hoàn thành ít nhất một khóa thuộc loại này.
    private long completedLeaderCount;
    // Đang là khóa tiên quyết hiện tại của loại khóa đang sửa.
    private boolean current;
    private boolean selectable;
    private String disabledReason;
}
