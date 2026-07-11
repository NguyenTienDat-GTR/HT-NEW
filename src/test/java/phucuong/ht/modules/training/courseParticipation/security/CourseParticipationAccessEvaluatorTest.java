package phucuong.ht.modules.training.courseParticipation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class CourseParticipationAccessEvaluatorTest {

    @Test
    void superAdminCannotScoreEvenWhenAuthorityIsPresent() {
        CourseParticipationAccessEvaluator evaluator = new CourseParticipationAccessEvaluator(
                new securityUtils(), mock(phucuong.ht.modules.auth.repository.AccountRepository.class),
                mock(phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository.class),
                mock(phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository.class),
                new phucuong.ht.modules.training.TrainingWorkflowSupport());
        var authentication = new UsernamePasswordAuthenticationToken(
                "super-admin", null,
                List.of(
                        () -> "ROLE_SUPER_ADMIN",
                        () -> CourseParticipationAccessEvaluator.SCORE_DIOCESE));

        assertFalse(evaluator.canScore(authentication, "participation-1"));
    }
}
