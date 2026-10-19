package phucuong.ht.modules.training;

import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.Optional;

@Component
public class TrainingWorkflowSupport {

    // Khóa do giáo hạt quản lý (trước đây là khóa DT: SMTL_DT, SMKN_DT).
    public boolean isDeaneryManaged(TrainingCourseType courseType) {
        return courseType != null && courseType.getManagementUnitType() == UnitType.DEANERY;
    }

    // Đơn vị gửi đăng ký: ưu tiên phạm vi gắn trực tiếp của tài khoản (vai trò giáo phận cũng có thể giữ quyền gửi của giáo xứ).
    public UnitType resolveRegistrationSender(
            boolean submitParish, boolean submitDiocese, boolean submitDeanery, boolean directDiocese, boolean directDeanery) {
        if (submitDiocese && directDiocese) {
            return UnitType.DIOCESE;
        }
        if (submitDeanery && directDeanery) {
            return UnitType.DEANERY;
        }
        if (submitParish) {
            return UnitType.PARISH;
        }
        if (submitDiocese) {
            return UnitType.DIOCESE;
        }
        if (submitDeanery) {
            return UnitType.DEANERY;
        }
        return null;
    }

    public Optional<TrainingCourseType> prerequisiteOf(TrainingCourseType courseType) {
        if (courseType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(courseType.getPrerequisiteType());
    }

    public Optional<LeaderLevel> certificateLevelFor(TrainingCourseType courseType) {
        if (courseType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(courseType.getResultingLevel());
    }

    public boolean needsAutomaticEligibilityApproval(TrainingCourseType courseType) {
        return courseType != null;
    }

    public boolean isPromotion(LeaderLevel currentLevel, LeaderLevel targetLevel) {
        return targetLevel != null && (currentLevel == null || targetLevel.ordinal() > currentLevel.ordinal());
    }

    // Hồ sơ đủ điều kiện được hệ thống tự duyệt ngay khi gửi đăng ký (khóa giáo hạt: giáo hạt duyệt; khóa giáo phận: giáo phận duyệt).
    public boolean shouldAutoApproveAfterSubmission(TrainingCourseType courseType) {
        return courseType != null;
    }
}
