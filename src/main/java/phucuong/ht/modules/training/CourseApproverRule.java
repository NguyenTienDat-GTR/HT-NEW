package phucuong.ht.modules.training;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.security.AuthorityChecks;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ai là người duyệt đăng ký của một khóa: tài khoản có quyền duyệt đúng cấp quản lý của khóa VÀ thuộc đúng đơn vị sở hữu khóa
 * (giáo hạt với khóa giáo hạt, giáo phận với khóa giáo phận). Một chỗ duy nhất giữ quy tắc này cho mọi nơi cần kiểm tra.
 */
public final class CourseApproverRule {

    private CourseApproverRule() {
    }

    /**
     * @param deaneryManaged  khóa do giáo hạt quản lý (TrainingWorkflowSupport.isDeaneryManaged)
     * @param actorDioceseId  giáo phận của tài khoản; chỉ được hỏi khi khóa cấp giáo phận và tài khoản có quyền duyệt cấp giáo phận
     * @param actorDeaneryId  giáo hạt của tài khoản; chỉ được hỏi khi khóa cấp giáo hạt và tài khoản có quyền duyệt cấp giáo hạt
     */
    public static boolean canApprove(boolean deaneryManaged, TrainingCourse course, Authentication authentication,
                                     Supplier<UUID> actorDioceseId, Supplier<UUID> actorDeaneryId) {
        if (deaneryManaged) {
            return AuthorityChecks.hasAuthority(authentication, CourseParticipationAccessEvaluator.APPROVE_DEANERY)
                    && course.getDeanery() != null
                    && course.getDeanery().getId().equals(actorDeaneryId.get());
        }
        return AuthorityChecks.hasAuthority(authentication, CourseParticipationAccessEvaluator.APPROVE_DIOCESE)
                && course.getDiocese() != null
                && course.getDiocese().getId().equals(actorDioceseId.get());
    }
}
