package phucuong.ht.modules.training.courseParticipation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseParticipationAccessEvaluatorTest {

    @Test
    void parishCanRegisterDtCourseOnlyInsideOwningDeanery() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
        CourseParticipationAccessEvaluator evaluator = evaluator(accountRepository, courseRepository, mock(CourseParticipationRepository.class));
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = course("DT-2026", CourseType.SMKN_DT);

        when(accountRepository.findActiveParishIdByUsername("parish-admin")).thenReturn(Optional.of(parishId));
        when(accountRepository.findActiveDeaneryIdByUsername("parish-admin")).thenReturn(Optional.of(deaneryId));
        when(courseRepository.findByCodeWithRequirement("DT-2026")).thenReturn(Optional.of(course));
        when(courseRepository.findDeaneryIdByCourseCode("DT-2026")).thenReturn(Optional.of(deaneryId));

        assertTrue(evaluator.canRegister(authentication(CourseParticipationAccessEvaluator.SUBMIT_PARISH), "DT-2026"));

        when(courseRepository.findDeaneryIdByCourseCode("DT-2026")).thenReturn(Optional.of(UUID.randomUUID()));
        assertFalse(evaluator.canRegister(authentication(CourseParticipationAccessEvaluator.SUBMIT_PARISH), "DT-2026"));
    }

    @Test
    void parishCanRegisterRegularCourseOnlyInsideOwningDiocese() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
        CourseParticipationAccessEvaluator evaluator = evaluator(accountRepository, courseRepository, mock(CourseParticipationRepository.class));
        UUID parishId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        TrainingCourse course = course("REGULAR-2026", CourseType.SMTL_I);

        when(accountRepository.findActiveParishIdByUsername("parish-admin")).thenReturn(Optional.of(parishId));
        when(accountRepository.findActiveDioceseIdByUsername("parish-admin")).thenReturn(Optional.of(dioceseId));
        when(courseRepository.findByCodeWithRequirement("REGULAR-2026")).thenReturn(Optional.of(course));
        when(courseRepository.findDioceseIdByCourseCode("REGULAR-2026")).thenReturn(Optional.of(dioceseId));

        assertTrue(evaluator.canRegister(authentication(CourseParticipationAccessEvaluator.SUBMIT_PARISH), "REGULAR-2026"));

        when(courseRepository.findDioceseIdByCourseCode("REGULAR-2026")).thenReturn(Optional.of(UUID.randomUUID()));
        assertFalse(evaluator.canRegister(authentication(CourseParticipationAccessEvaluator.SUBMIT_PARISH), "REGULAR-2026"));
    }

    @Test
    void parishCannotApproveDtRegistrationEvenWithApprovalAuthority() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
        CourseParticipationRepository participationRepository = mock(CourseParticipationRepository.class);
        CourseParticipationAccessEvaluator evaluator = evaluator(accountRepository, courseRepository, participationRepository);
        TrainingCourse course = course("DT-2026", CourseType.SMKN_DT);
        CourseParticipation participation = new CourseParticipation();
        participation.setCourse(course);

        when(participationRepository.findByIdWithLeaderAndCourse("participation-1")).thenReturn(Optional.of(participation));
        when(accountRepository.findDirectActiveDeaneryIdByUsername("parish-admin")).thenReturn(Optional.empty());

        assertFalse(evaluator.canDeaneryApprove(authentication(CourseParticipationAccessEvaluator.APPROVE_DEANERY), "participation-1"));
    }

    @Test
    void parishCanReadOnlyParticipationsForLeadersInOwnParish() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
        CourseParticipationRepository participationRepository = mock(CourseParticipationRepository.class);
        CourseParticipationAccessEvaluator evaluator = evaluator(accountRepository, courseRepository, participationRepository);
        UUID parishId = UUID.randomUUID();
        CourseParticipation participation = participationWithLeaderParish(parishId, CourseType.SMTL_I);

        when(accountRepository.findActiveParishIdByUsername("parish-admin")).thenReturn(Optional.of(parishId));
        when(participationRepository.findByIdWithLeaderAndCourse("participation-1")).thenReturn(Optional.of(participation));

        assertTrue(evaluator.canReadOne(authentication(CourseParticipationAccessEvaluator.READ_PARISH), "participation-1"));

        when(accountRepository.findActiveParishIdByUsername("parish-admin")).thenReturn(Optional.of(UUID.randomUUID()));
        assertFalse(evaluator.canReadOne(authentication(CourseParticipationAccessEvaluator.READ_PARISH), "participation-1"));
    }

    @Test
    void superAdminCannotScoreEvenWhenAuthorityIsPresent() {
        CourseParticipationAccessEvaluator evaluator = evaluator(
                mock(AccountRepository.class),
                mock(TrainingCourseRepository.class),
                mock(CourseParticipationRepository.class));
        var authentication = new UsernamePasswordAuthenticationToken(
                "super-admin", null,
                List.of(
                        () -> "ROLE_SUPER_ADMIN",
                        () -> CourseParticipationAccessEvaluator.SCORE_DIOCESE));

        assertFalse(evaluator.canScore(authentication, "participation-1"));
    }

    private CourseParticipationAccessEvaluator evaluator(
            AccountRepository accountRepository,
            TrainingCourseRepository courseRepository,
            CourseParticipationRepository participationRepository
    ) {
        return new CourseParticipationAccessEvaluator(
                new securityUtils(), accountRepository, courseRepository, participationRepository, new TrainingWorkflowSupport());
    }

    private TrainingCourse course(String code, CourseType courseType) {
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseType(courseType);
        return course;
    }

    private CourseParticipation participationWithLeaderParish(UUID parishId, CourseType courseType) {
        Parish parish = new Parish();
        parish.setId(parishId);
        Leader leader = new Leader();
        leader.setParish(parish);
        CourseParticipation participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course("COURSE-2026", courseType));
        return participation;
    }

    private UsernamePasswordAuthenticationToken authentication(String authority) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "parish-admin", null, List.of("ADMIN_PARISH"), List.of(authority));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
