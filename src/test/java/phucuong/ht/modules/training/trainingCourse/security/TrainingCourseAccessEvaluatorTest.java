package phucuong.ht.modules.training.trainingCourse.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tài khoản nhiều vai trò (admin giáo hạt + admin giáo xứ) chỉ được sửa, bật/tắt, kết thúc và khóa điểm khóa thuộc
 * đúng cấp mình có quyền: khóa giáo phận cần quyền cấp giáo phận dù khóa đó hiện trong danh sách của tài khoản.
 */
class TrainingCourseAccessEvaluatorTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
    private final TrainingCourseAccessEvaluator evaluator = new TrainingCourseAccessEvaluator(
            new securityUtils(), accountRepository, courseRepository, mock(TrainingCourseTypeRepository.class), new TrainingWorkflowSupport());
    private final UUID dioceseId = UUID.randomUUID();
    private final UUID deaneryId = UUID.randomUUID();

    private static final List<String> DEANERY_ADMIN_PLUS_PARISH_ADMIN = List.of(
            "training.course.read.deanery", "training.course.update.deanery", "training.course.toggle.deanery", "training.course.finish.deanery",
            "training.participation.lock.deanery", "training.participation.submit.parish", "training.participation.read.parish");

    private Authentication authentication(List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("multi-role", null, List.of("ADMIN_DEANERY", "ADMIN_PARISH"), permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void course(String code, UnitType managedBy) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code + "-TYPE");
        type.setManagementUnitType(managedBy);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseType(type);
        when(courseRepository.findByCodeWithType(code)).thenReturn(Optional.of(course));
        when(courseRepository.findDioceseIdByCourseCode(code)).thenReturn(managedBy == UnitType.DEANERY ? Optional.empty() : Optional.of(dioceseId));
        when(courseRepository.findDeaneryIdByCourseCode(code)).thenReturn(managedBy == UnitType.DEANERY ? Optional.of(deaneryId) : Optional.empty());
        when(accountRepository.findActiveDioceseIdByUsername("multi-role")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.of(deaneryId));
    }

    @Test
    void deaneryPlusParishAccountCannotChangeAFinishOrLockAScoreOfADioceseCourseInItsOwnDiocese() {
        course("DIOCESE-COURSE", UnitType.DIOCESE);
        Authentication auth = authentication(DEANERY_ADMIN_PLUS_PARISH_ADMIN);

        assertFalse(evaluator.canUpdate(auth, "DIOCESE-COURSE"));
        assertFalse(evaluator.canToggle(auth, "DIOCESE-COURSE"));
        assertFalse(evaluator.canFinish(auth, "DIOCESE-COURSE"));
        assertFalse(evaluator.canLockScore(auth, "DIOCESE-COURSE"));
    }

    @Test
    void sameAccountStillManagesItsOwnDeaneryCourse() {
        course("DEANERY-COURSE", UnitType.DEANERY);
        Authentication auth = authentication(DEANERY_ADMIN_PLUS_PARISH_ADMIN);

        assertTrue(evaluator.canUpdate(auth, "DEANERY-COURSE"));
        assertTrue(evaluator.canToggle(auth, "DEANERY-COURSE"));
        assertTrue(evaluator.canFinish(auth, "DEANERY-COURSE"));
        assertTrue(evaluator.canLockScore(auth, "DEANERY-COURSE"));
    }

    @Test
    void deaneryAccountCannotManageADeaneryCourseOfAnotherDeanery() {
        course("OTHER-DEANERY-COURSE", UnitType.DEANERY);
        when(courseRepository.findDeaneryIdByCourseCode("OTHER-DEANERY-COURSE")).thenReturn(Optional.of(UUID.randomUUID()));

        assertFalse(evaluator.canUpdate(authentication(DEANERY_ADMIN_PLUS_PARISH_ADMIN), "OTHER-DEANERY-COURSE"));
    }
}
