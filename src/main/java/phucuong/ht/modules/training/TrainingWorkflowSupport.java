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

    public boolean shouldAutoApproveAfterParishSubmission(TrainingCourseType courseType) {
        return isDeaneryManaged(courseType);
    }

    public boolean shouldAutoApproveAfterDeanerySubmission(TrainingCourseType courseType) {
        return courseType != null && !isDeaneryManaged(courseType);
    }
}
