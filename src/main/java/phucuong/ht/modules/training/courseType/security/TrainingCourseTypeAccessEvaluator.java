package phucuong.ht.modules.training.courseType.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;

@Component("trainingCourseTypeAccessEvaluator")
@RequiredArgsConstructor
public class TrainingCourseTypeAccessEvaluator {

    public static final String CREATE_DIOCESE = "training.course_type.create.diocese";
    public static final String READ_DIOCESE = "training.course_type.read.diocese";
    public static final String UPDATE_DIOCESE = "training.course_type.update.diocese";
    public static final String TOGGLE_DIOCESE = "training.course_type.toggle.diocese";
    public static final String CREATE_DEANERY = "training.course_type.create.deanery";
    public static final String READ_DEANERY = "training.course_type.read.deanery";
    public static final String UPDATE_DEANERY = "training.course_type.update.deanery";
    public static final String TOGGLE_DEANERY = "training.course_type.toggle.deanery";

    private final securityUtils securityUtils;
    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final CourseTypeOwnership ownership;

    // Catalog toàn hệ thống: tạo/sửa/đổi trạng thái theo cấp quản lý của loại khóa (giáo phận hoặc giáo hạt).
    public boolean canCreate(Authentication authentication, UnitType managementUnitType) {
        return managementUnitType == UnitType.DEANERY
                ? securityUtils.hasAuthority(authentication, CREATE_DEANERY)
                : securityUtils.hasAuthority(authentication, CREATE_DIOCESE);
    }

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, READ_DEANERY);
    }

    // Chọn khóa tiên quyết khi tạo/sửa loại khóa ở cấp managementUnitType (null = chưa rõ cấp, chỉ cần quyền tạo/sửa ở một cấp).
    public boolean canPickPrerequisite(Authentication authentication, UnitType managementUnitType) {
        boolean diocese = securityUtils.hasAuthority(authentication, CREATE_DIOCESE) || securityUtils.hasAuthority(authentication, UPDATE_DIOCESE);
        boolean deanery = securityUtils.hasAuthority(authentication, CREATE_DEANERY) || securityUtils.hasAuthority(authentication, UPDATE_DEANERY);
        if (managementUnitType == UnitType.DIOCESE) {
            return diocese;
        }
        if (managementUnitType == UnitType.DEANERY) {
            return deanery;
        }
        return diocese || deanery;
    }

    public boolean canRead(Authentication authentication, String typeCode) {
        return canList(authentication);
    }

    public boolean canUpdate(Authentication authentication, String typeCode) {
        return hasScopedWriteAccess(authentication, typeCode, UPDATE_DIOCESE, UPDATE_DEANERY);
    }

    public boolean canToggle(Authentication authentication, String typeCode) {
        return hasScopedWriteAccess(authentication, typeCode, TOGGLE_DIOCESE, TOGGLE_DEANERY);
    }

    private boolean hasScopedWriteAccess(Authentication authentication, String typeCode, String dioceseAuthority, String deaneryAuthority) {
        if (typeCode == null || typeCode.isBlank()) {
            return false;
        }
        return trainingCourseTypeRepository.findByCodeWithPrerequisite(typeCode)
                .map(courseType -> (courseType.getManagementUnitType() == UnitType.DEANERY
                        ? securityUtils.hasAuthority(authentication, deaneryAuthority)
                        : securityUtils.hasAuthority(authentication, dioceseAuthority))
                        // Loại khóa do giáo phận quản lý thì giáo hạt không sửa được; loại giáo hạt tự tạo thì giáo phận không sửa được.
                        && ownership.canManage(courseType, authentication))
                .orElse(false);
    }
}
