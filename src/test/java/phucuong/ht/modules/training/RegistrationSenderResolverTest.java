package phucuong.ht.modules.training;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationSenderResolverTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
    private final RegistrationSenderResolver resolver = new RegistrationSenderResolver(
            new securityUtils(), accountRepository, courseRepository, new TrainingWorkflowSupport(), new CommonValidationHelper(new securityUtils()));
    private final UUID dioceseId = UUID.randomUUID();
    private final UUID deaneryId = UUID.randomUUID();
    private final UUID parishId = UUID.randomUUID();

    private Authentication authentication(String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("multi-role", null, List.of("ADMIN_DEANERY", "ADMIN_PARISH"), List.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void units() {
        when(accountRepository.findActiveDioceseIdByUsername("multi-role")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveParishIdByUsername("multi-role")).thenReturn(Optional.of(parishId));
    }

    private TrainingCourse course(String code, UnitType managedBy) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code + "-TYPE");
        type.setManagementUnitType(managedBy);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseType(type);
        return course;
    }

    @Test
    void deaneryPlusParishAccountHasBothSendersHighestFirst() {
        units();

        var senders = resolver.available(authentication(CourseParticipationAccessEvaluator.SUBMIT_DEANERY, CourseParticipationAccessEvaluator.SUBMIT_PARISH));

        assertEquals(List.of(UnitType.DEANERY, UnitType.PARISH), senders);
    }

    @Test
    void deaneryRoleLinkedOnlyThroughALeaderStillCountsAsADeanerySender() {
        // Không có liên kết giáo hạt trực tiếp trên tài khoản: chỉ cần giáo hạt hiệu lực (suy ra từ huynh trưởng).
        units();

        assertEquals(List.of(UnitType.DEANERY), resolver.available(authentication(CourseParticipationAccessEvaluator.SUBMIT_DEANERY)));
    }

    @Test
    void requestedSenderMustBeOneTheAccountHas() {
        units();
        Authentication auth = authentication(CourseParticipationAccessEvaluator.SUBMIT_DEANERY, CourseParticipationAccessEvaluator.SUBMIT_PARISH);

        assertEquals(UnitType.PARISH, resolver.resolve(auth, UnitType.PARISH));
        assertEquals(UnitType.DEANERY, resolver.resolve(auth, null));
        assertThrows(ApiException.class, () -> resolver.resolve(auth, UnitType.DIOCESE));
    }

    @Test
    void accountWithoutAnySubmitPermissionIsRejected() {
        units();

        assertThrows(ApiException.class, () -> resolver.resolve(authentication("training.course.read.deanery"), null));
    }

    @Test
    void defaultSenderIsTheOneThatFitsTheCourse() {
        units();
        // Khóa giáo hạt của một giáo hạt khác: giáo hạt/giáo xứ của tài khoản đều không phù hợp -> quay về cấp cao nhất.
        Authentication auth = authentication(CourseParticipationAccessEvaluator.SUBMIT_DEANERY, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
        TrainingCourse dioceseCourse = course("REG", UnitType.DIOCESE);
        when(courseRepository.findDioceseIdByCourseCode("REG")).thenReturn(Optional.of(dioceseId));

        assertEquals(List.of(UnitType.DEANERY, UnitType.PARISH), resolver.availableForCourse(auth, dioceseCourse));
        assertEquals(UnitType.DEANERY, resolver.resolveForCourse(auth, null, dioceseCourse));
    }

    @Test
    void dioceseSenderOnlyFitsCoursesOwnedByItsDiocese() {
        units();
        Authentication auth = authentication(CourseParticipationAccessEvaluator.SUBMIT_DIOCESE, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
        TrainingCourse deaneryCourse = course("DT", UnitType.DEANERY);
        when(courseRepository.findDeaneryIdByCourseCode("DT")).thenReturn(Optional.of(deaneryId));

        assertTrue(resolver.availableForCourse(auth, deaneryCourse).contains(UnitType.PARISH));
        assertEquals(List.of(UnitType.PARISH), resolver.availableForCourse(auth, deaneryCourse));
    }
}
