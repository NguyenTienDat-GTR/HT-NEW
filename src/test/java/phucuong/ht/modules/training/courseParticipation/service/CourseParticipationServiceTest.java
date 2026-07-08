package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseParticipationServiceTest {

    @Mock
    private CourseParticipationRepository courseParticipationRepository;

    @Mock
    private TrainingCourseRepository trainingCourseRepository;

    @Mock
    private LeaderRepository leaderRepository;

    @Mock
    private ParishRepository parishRepository;

    @Mock
    private DeaneryRepository deaneryRepository;

    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private TrainingNotificationRecipientResolver trainingNotificationRecipientResolver;

    @Mock
    private TrainingNotificationService trainingNotificationService;

    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(new securityUtils());

    @InjectMocks
    private CourseParticipationService courseParticipationService;

    @Test
    void registerShouldRequireExceptionReasonWhenLeaderNotEligible() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course(CourseType.SMKN_DT);
        Leader leader = leader(leaderId, parishId, LeaderLevel.HT_XU);
        CourseRequirement requirement = new CourseRequirement();
        requirement.setRequiredCurrentLevel(LeaderLevel.DU_TRUONG);
        course.setRequirement(requirement);

        Parish parish = parish(parishId);
        when(trainingCourseRepository.findByCodeWithRequirement("SMKNDT-2026")).thenReturn(Optional.of(course));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register("SMKNDT-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(trainingNotificationService, never()).notifyUsers(any(), any());
    }

    @Test
    void registerShouldRejectWhenRegistrationWindowNotOpen() {
        courseParticipationService = buildService();

        TrainingCourse course = course(CourseType.SMKN_DT);
        course.setRegistrationStartAt(OffsetDateTime.now().plusDays(1));
        course.setRegistrationEndAt(OffsetDateTime.now().plusDays(2));

        when(trainingCourseRepository.findByCodeWithRequirement("SMKNDT-2026")).thenReturn(Optional.of(course));

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(UUID.randomUUID()));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register("SMKNDT-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(domainEventPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void registerShouldReturnBatchSummaryForDioceseCourseWithoutAutoApprovalAtParishStep() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course(CourseType.SMTL_I);
        Leader leader = leader(leaderId, parishId, LeaderLevel.HT_XU);
        Parish parish = parish(parishId);

        when(trainingCourseRepository.findByCodeWithRequirement("SMTLI-2026")).thenReturn(Optional.of(course));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);
        when(trainingNotificationRecipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of("receiver"));

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));

        var response = courseParticipationService.register("SMTLI-2026", request, authentication("admin-parish", "training.participation.submit.parish"));

        assertEquals(1, response.getTotalCount());
        assertEquals(0, response.getAutoApprovedCount());
        assertEquals(1, response.getPendingCount());
        verify(trainingNotificationService).notifyUsers(any(), any());
        verify(domainEventPublisher).publishAfterCommit(any());
    }

    @Test
    void scoreShouldMarkCertificatePendingWhenPassing() {
        courseParticipationService = buildService();

        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-1");
        participation.setCourse(course(CourseType.SMKN_I));
        participation.setLeader(leader(UUID.randomUUID(), UUID.randomUUID(), LeaderLevel.HT_XU));

        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-1")).thenReturn(Optional.of(participation));

        TrainingScoreRequest request = new TrainingScoreRequest();
        request.setTotalScore(BigDecimal.valueOf(8));
        request.setReview("pass");

        var response = courseParticipationService.score("cp-1", request, authentication("admin-diocese", "training.participation.score.diocese"));

        assertEquals(WorkflowApprovalStatus.PENDING, response.getCertificateApprovalStatus());
        verify(domainEventPublisher).publishAfterCommit(any());
    }

    private CourseParticipationService buildService() {
        return new CourseParticipationService(
                courseParticipationRepository,
                trainingCourseRepository,
                leaderRepository,
                parishRepository,
                deaneryRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                commonValidationHelper,
                domainEventPublisher,
                trainingNotificationRecipientResolver,
                trainingNotificationService
        );
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String permission) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, "leader-1", List.of("ADMIN_PARISH"), List.of(permission));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private TrainingCourse course(CourseType courseType) {
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("COURSE");
        course.setCourseType(courseType);
        course.setRegistrationStartAt(OffsetDateTime.now().minusDays(1));
        course.setRegistrationEndAt(OffsetDateTime.now().plusDays(1));
        return course;
    }

    private Leader leader(UUID leaderId, UUID parishId, LeaderLevel level) {
        Leader leader = new Leader();
        leader.setId(leaderId);
        leader.setLeaderLevel(level);
        leader.setBirthDate(LocalDate.now().minusYears(20));
        leader.setCreatedAt(OffsetDateTime.now().minusYears(5));
        Parish parish = new Parish();
        parish.setId(parishId);
        leader.setParish(parish);
        return leader;
    }

    private Parish parish(UUID parishId) {
        Parish parish = new Parish();
        parish.setId(parishId);
        parish.setName("Parish A");
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setName("Deanery A");
        parish.setDeanery(deanery);
        return parish;
    }
}
