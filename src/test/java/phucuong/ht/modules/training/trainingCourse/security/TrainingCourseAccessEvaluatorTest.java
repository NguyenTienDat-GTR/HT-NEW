package phucuong.ht.modules.training.trainingCourse.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
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

    private static final List<String> DEANERY_ADMIN_ONLY = List.of(
            "training.course.read.deanery", "training.participation.submit.deanery");

    // Khóa giáo phận của giáo phận ownerDioceseId; tài khoản "multi-role" thuộc giáo phận dioceseId (cả hai giáo hạt / giáo xứ đều hoạt động).
    private void dioceseCourse(String code, UUID ownerDioceseId, CourseRegistrationStatus registrationStatus, boolean active) {
        course(code, UnitType.DIOCESE);
        Diocese owner = new Diocese();
        owner.setId(ownerDioceseId);
        TrainingCourse course = courseRepository.findByCodeWithType(code).orElseThrow();
        course.setDiocese(owner);
        course.setRegistrationStatus(registrationStatus);
        course.setStatus(active);
        when(accountRepository.findActiveParishIdByUsername("multi-role")).thenReturn(Optional.empty());
    }

    @Test
    void deaneryAdminWithoutParishRoleSeesAnOpenCourseOfItsOwnDiocese() {
        dioceseCourse("OPEN-DIOCESE-COURSE", dioceseId, CourseRegistrationStatus.OPEN, true);
        Authentication auth = authentication(DEANERY_ADMIN_ONLY);

        assertTrue(evaluator.canList(auth));
        assertTrue(evaluator.canRead(auth, "OPEN-DIOCESE-COURSE"));
        // Chỉ được xem, không được sửa hay quản lý khóa giáo phận.
        assertFalse(evaluator.canUpdate(auth, "OPEN-DIOCESE-COURSE"));
        assertFalse(evaluator.canFinish(auth, "OPEN-DIOCESE-COURSE"));
    }

    @Test
    void deaneryAdminDoesNotSeeDioceseCourseThatIsNotOpenOrBelongsToAnotherDiocese() {
        Authentication auth = authentication(DEANERY_ADMIN_ONLY);

        dioceseCourse("CLOSED-DIOCESE-COURSE", dioceseId, CourseRegistrationStatus.CLOSED, true);
        assertFalse(evaluator.canRead(auth, "CLOSED-DIOCESE-COURSE"));

        dioceseCourse("DISABLED-DIOCESE-COURSE", dioceseId, CourseRegistrationStatus.OPEN, false);
        assertFalse(evaluator.canRead(auth, "DISABLED-DIOCESE-COURSE"));

        dioceseCourse("OTHER-DIOCESE-COURSE", UUID.randomUUID(), CourseRegistrationStatus.OPEN, true);
        when(accountRepository.findActiveDioceseIdByUsername("multi-role")).thenReturn(Optional.of(dioceseId));
        assertFalse(evaluator.canRead(auth, "OTHER-DIOCESE-COURSE"));
    }

    @Test
    void deaneryAdminWhoseDeaneryIsInactiveSeesNothing() {
        dioceseCourse("OPEN-DIOCESE-COURSE", dioceseId, CourseRegistrationStatus.OPEN, true);
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.empty());

        assertFalse(evaluator.canRead(authentication(DEANERY_ADMIN_ONLY), "OPEN-DIOCESE-COURSE"));
    }

    @Test
    void deaneryAccountCannotManageADeaneryCourseOfAnotherDeanery() {
        course("OTHER-DEANERY-COURSE", UnitType.DEANERY);
        when(courseRepository.findDeaneryIdByCourseCode("OTHER-DEANERY-COURSE")).thenReturn(Optional.of(UUID.randomUUID()));

        assertFalse(evaluator.canUpdate(authentication(DEANERY_ADMIN_PLUS_PARISH_ADMIN), "OTHER-DEANERY-COURSE"));
    }
}
