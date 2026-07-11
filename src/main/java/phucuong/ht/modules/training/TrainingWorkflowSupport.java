package phucuong.ht.modules.training;

import org.springframework.stereotype.Component;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.enums.CourseType;

import java.util.EnumSet;
import java.util.Optional;

@Component
public class TrainingWorkflowSupport {

    private static final EnumSet<CourseType> DT_COURSE_TYPES = EnumSet.of(CourseType.SMTL_DT, CourseType.SMKN_DT);

    public boolean isDeaneryManaged(CourseType courseType) {
        return courseType != null && DT_COURSE_TYPES.contains(courseType);
    }

    public Optional<CourseType> prerequisiteOf(CourseType courseType) {
        if (courseType == null) {
            return Optional.empty();
        }

        return switch (courseType) {
            case SMKN_DT -> Optional.of(CourseType.SMTL_DT);
            case SMKN_I -> Optional.of(CourseType.SMTL_I);
            case TSM_II -> Optional.of(CourseType.SMKN_I);
            case SMTL_II -> Optional.of(CourseType.TSM_II);
            case SMKN_II -> Optional.of(CourseType.SMTL_II);
            case TSM_III_1 -> Optional.of(CourseType.SMKN_II);
            case TSM_III_2 -> Optional.of(CourseType.TSM_III_1);
            case SMTL_III -> Optional.of(CourseType.TSM_III_2);
            case SMKN_III -> Optional.of(CourseType.SMTL_III);
            default -> Optional.empty();
        };
    }

    public Optional<LeaderLevel> certificateLevelFor(CourseType courseType) {
        if (courseType == null) {
            return Optional.empty();
        }

        return switch (courseType) {
            case SMKN_DT -> Optional.of(LeaderLevel.DU_TRUONG);
            case SMKN_I -> Optional.of(LeaderLevel.HT_I);
            case SMKN_II -> Optional.of(LeaderLevel.HT_II);
            case SMKN_III -> Optional.of(LeaderLevel.HT_III);
            case HLV_I -> Optional.of(LeaderLevel.HLV_I);
            case HLV_II -> Optional.of(LeaderLevel.HLV_II);
            case HLV_III -> Optional.of(LeaderLevel.HLV_III);
            default -> Optional.empty();
        };
    }

    public boolean needsAutomaticEligibilityApproval(CourseType courseType) {
        return courseType != null;
    }

    public boolean isPromotion(LeaderLevel currentLevel, LeaderLevel targetLevel) {
        return targetLevel != null && (currentLevel == null || targetLevel.ordinal() > currentLevel.ordinal());
    }

    public boolean shouldAutoApproveAfterParishSubmission(CourseType courseType) {
        return isDeaneryManaged(courseType);
    }

    public boolean shouldAutoApproveAfterDeanerySubmission(CourseType courseType) {
        return courseType != null && !isDeaneryManaged(courseType);
    }
}
