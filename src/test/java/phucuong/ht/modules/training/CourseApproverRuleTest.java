package phucuong.ht.modules.training;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Người duyệt đăng ký của khóa: đúng quyền theo cấp quản lý VÀ đúng đơn vị sở hữu khóa. */
class CourseApproverRuleTest {

    private static final UUID DIOCESE = UUID.randomUUID();
    private static final UUID DEANERY = UUID.randomUUID();
    private static final Supplier<UUID> MUST_NOT_ASK = () -> {
        throw new AssertionError("Không được tra đơn vị khi chưa cần");
    };

    @Test
    void deaneryCourseNeedsDeaneryApprovalPermissionAndTheSameDeanery() {
        TrainingCourse course = deaneryCourse();
        Authentication approver = auth(CourseParticipationAccessEvaluator.APPROVE_DEANERY);

        assertTrue(CourseApproverRule.canApprove(true, course, approver, MUST_NOT_ASK, () -> DEANERY));
        assertFalse(CourseApproverRule.canApprove(true, course, approver, MUST_NOT_ASK, UUID::randomUUID), "giáo hạt khác");
        assertFalse(CourseApproverRule.canApprove(true, course, approver, MUST_NOT_ASK, () -> null), "tài khoản không gắn giáo hạt");
        // Chỉ có quyền duyệt cấp giáo phận thì không duyệt được khóa giáo hạt, và không cần tra đơn vị.
        assertFalse(CourseApproverRule.canApprove(true, course, auth(CourseParticipationAccessEvaluator.APPROVE_DIOCESE), MUST_NOT_ASK, MUST_NOT_ASK));
    }

    @Test
    void dioceseCourseNeedsDioceseApprovalPermissionAndTheSameDiocese() {
        TrainingCourse course = dioceseCourse();
        Authentication approver = auth(CourseParticipationAccessEvaluator.APPROVE_DIOCESE);

        assertTrue(CourseApproverRule.canApprove(false, course, approver, () -> DIOCESE, MUST_NOT_ASK));
        assertFalse(CourseApproverRule.canApprove(false, course, approver, UUID::randomUUID, MUST_NOT_ASK), "giáo phận khác");
        assertFalse(CourseApproverRule.canApprove(false, course, approver, () -> null, MUST_NOT_ASK), "tài khoản không gắn giáo phận");
        assertFalse(CourseApproverRule.canApprove(false, course, auth(CourseParticipationAccessEvaluator.APPROVE_DEANERY), MUST_NOT_ASK, MUST_NOT_ASK));
    }

    @Test
    void courseWithoutOwnerUnitCannotBeApprovedByAnyone() {
        assertFalse(CourseApproverRule.canApprove(true, new TrainingCourse(), auth(CourseParticipationAccessEvaluator.APPROVE_DEANERY), MUST_NOT_ASK, MUST_NOT_ASK));
        assertFalse(CourseApproverRule.canApprove(false, new TrainingCourse(), auth(CourseParticipationAccessEvaluator.APPROVE_DIOCESE), MUST_NOT_ASK, MUST_NOT_ASK));
    }

    private static TrainingCourse deaneryCourse() {
        Deanery deanery = new Deanery();
        deanery.setId(DEANERY);
        TrainingCourse course = new TrainingCourse();
        course.setDeanery(deanery);
        return course;
    }

    private static TrainingCourse dioceseCourse() {
        Diocese diocese = new Diocese();
        diocese.setId(DIOCESE);
        TrainingCourse course = new TrainingCourse();
        course.setDiocese(diocese);
        return course;
    }

    private static Authentication auth(String permission) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("approver", null, List.of(), List.of(permission));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
