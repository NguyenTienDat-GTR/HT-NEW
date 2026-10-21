package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.certificate.CertificateCodeGenerator;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.model.request.ComponentScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;
import phucuong.ht.modules.training.scoreComponentTemplate.repository.ScoreComponentTemplateRepository;
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
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateCodeGenerator certificateCodeGenerator;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private TrainingNotificationRecipientResolver trainingNotificationRecipientResolver;

    @Mock
    private TrainingNotificationService trainingNotificationService;

    @Mock
    private ScoreComponentTemplateRepository scoreComponentTemplateRepository;
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(new securityUtils());

    @InjectMocks
    private CourseParticipationService courseParticipationService;

    @Test
    void findAllShouldScopeParishAdminToLeadersInOwnParish() {
        courseParticipationService = buildService();
        UUID parishId = UUID.randomUUID();
        ArgumentCaptor<BaseSearchRequest> requestCaptor = ArgumentCaptor.forClass(BaseSearchRequest.class);

        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);
        when(courseParticipationRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(List.of()));

        courseParticipationService.findAll(new BaseSearchRequest(), authentication("admin-parish", CourseParticipationAccessEvaluator.READ_PARISH));

        verify(courseParticipationRepository).findAll(requestCaptor.capture());
        assertEquals(parishId, requestCaptor.getValue().getFilters().get("leader.parish.id"));
    }

    @Test
    void registerShouldRequireExceptionReasonWhenLeaderNotEligible() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course("SMKN_DT", UnitType.DEANERY);
        Leader leader = leader(leaderId, parishId, "HT_XU");
        course.getCourseType().setRequiredCurrentLevel("DU_TRUONG");

        Parish parish = parish(parishId, course);
        when(trainingCourseRepository.findByCodeWithType("SMKNDT-2026")).thenReturn(Optional.of(course));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register("SMKNDT-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(trainingNotificationService, never()).notifyUsers(any(), any());
    }

    @Test
    void registerShouldRejectLeaderPresentInBothStandardAndExceptionLists() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course("SMKN_DT", UnitType.DEANERY);
        Parish parish = parish(parishId, course);
        when(trainingCourseRepository.findByCodeWithType("SMKNDT-2026")).thenReturn(Optional.of(course));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));
        request.setExceptionReasonsByLeaderId(java.util.Map.of(leaderId, "Đặc cách"));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register("SMKNDT-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(trainingNotificationService, never()).notifyUsers(any(), any());
    }

    @Test
    void registerShouldRejectWhenRegistrationWindowNotOpen() {
        courseParticipationService = buildService();

        TrainingCourse course = course("SMKN_DT", UnitType.DEANERY);
        course.setRegistrationStartAt(OffsetDateTime.now().plusDays(1));
        course.setRegistrationEndAt(OffsetDateTime.now().plusDays(2));

        when(trainingCourseRepository.findByCodeWithType("SMKNDT-2026")).thenReturn(Optional.of(course));

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(UUID.randomUUID()));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register("SMKNDT-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(domainEventPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void registerShouldRequireExceptionReasonWhenRequiredBirthDateIsMissing() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        course.getCourseType().setRequiredMinAge(18);
        Leader leader = leader(leaderId, parishId, "HT_XU");
        leader.setBirthDate(null);

        when(trainingCourseRepository.findByCodeWithType("SMTLI-2026")).thenReturn(Optional.of(course));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish(parishId, course)));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register(
                "SMTLI-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(courseParticipationRepository, never()).save(any());
    }

    @Test
    void registerShouldRejectInactiveLeader() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        Leader leader = leader(leaderId, parishId, "HT_XU");
        leader.setStatus(Boolean.FALSE);

        when(trainingCourseRepository.findByCodeWithType("SMTLI-2026")).thenReturn(Optional.of(course));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish(parishId, course)));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register(
                "SMTLI-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(courseParticipationRepository, never()).save(any());
    }

    @Test
    void registerShouldRejectParishOutsideDeaneryForDtCourse() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        TrainingCourse course = course("SMKN_DT", UnitType.DEANERY);
        Parish foreignParish = parish(parishId, course);
        Deanery foreignDeanery = new Deanery();
        foreignDeanery.setId(UUID.randomUUID());
        foreignDeanery.setDiocese(course.getDiocese());
        foreignParish.setDeanery(foreignDeanery);

        when(trainingCourseRepository.findByCodeWithType("SMKNDT-2026")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(foreignParish));

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(UUID.randomUUID()));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register(
                "SMKNDT-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(courseParticipationRepository, never()).save(any());
    }

    @Test
    void registerShouldRejectParishOutsideDioceseForRegularCourse() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        Parish foreignParish = parish(parishId, course);
        Diocese foreignDiocese = new Diocese();
        foreignDiocese.setId(UUID.randomUUID());
        Deanery foreignDeanery = new Deanery();
        foreignDeanery.setId(UUID.randomUUID());
        foreignDeanery.setDiocese(foreignDiocese);
        foreignParish.setDeanery(foreignDeanery);

        when(trainingCourseRepository.findByCodeWithType("SMTLI-2026")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(foreignParish));

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(UUID.randomUUID()));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register(
                "SMTLI-2026", request, authentication("admin-parish", "training.participation.submit.parish")));
        verify(courseParticipationRepository, never()).save(any());
    }

    @Test
    void registerShouldAutoApproveEligibleLeaderForDioceseCourse() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        Leader leader = leader(leaderId, parishId, "HT_XU");
        Parish parish = parish(parishId, course);

        when(trainingCourseRepository.findByCodeWithType("SMTLI-2026")).thenReturn(Optional.of(course));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(parishRepository.findByIdWithDeaneryAndDiocese(parishId)).thenReturn(Optional.of(parish));
        when(accountScopeValidationHelper.requireActiveParishId(any(), any())).thenReturn(parishId);
        when(trainingNotificationRecipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of("receiver"));

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(leaderId));

        var response = courseParticipationService.register("SMTLI-2026", request, authentication("admin-parish", "training.participation.submit.parish"));

        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getAutoApprovedCount());
        assertEquals(0, response.getPendingCount());
        verify(trainingNotificationService).notifyUsers(any(), any());
        verify(domainEventPublisher).publishAfterCommit(any());
    }

    @Test
    void registerShouldRejectDeanerySenderForDioceseCourse() {
        courseParticipationService = buildService();

        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        when(trainingCourseRepository.findByCodeWithType("SMTLI-2026")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(any(), any())).thenReturn(deaneryId);

        TrainingRegistrationRequest request = new TrainingRegistrationRequest();
        request.setLeaderIds(List.of(UUID.randomUUID()));

        assertThrows(RuntimeException.class, () -> courseParticipationService.register("SMTLI-2026", request, authentication("admin-deanery", "training.participation.submit.deanery")));
        verify(courseParticipationRepository, never()).save(any());
    }

    @Test
    void scoreShouldMarkCertificatePendingWhenPassing() {
        courseParticipationService = buildService();

        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-1");
        TrainingCourse course = course("SMKN_I", UnitType.DIOCESE);
        course.setCourseName("SMKN I");
        course.setHostYear(2026);
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName("Phu Cuong");
        course.setDiocese(diocese);
        course.setPassingScore(BigDecimal.valueOf(8));
        // Tinh than he so 2, ky nang he so 1, "chuyen can" he so 1 nhung khong tinh vao diem tong ket.
        ScoreComponentTemplate tinhThan = component("component-1", "TINH_THAN", BigDecimal.valueOf(10), 0, BigDecimal.valueOf(2), true);
        ScoreComponentTemplate kyNang = component("component-2", "KY_NANG", BigDecimal.valueOf(10), 1, BigDecimal.ONE, true);
        ScoreComponentTemplate chuyenCan = component("component-3", "CHUYEN_CAN", BigDecimal.valueOf(10), 2, BigDecimal.ONE, false);
        when(scoreComponentTemplateRepository.findActiveForCourse(org.mockito.ArgumentMatchers.eq("SMKN_I"), any(), any()))
                .thenReturn(List.of(tinhThan, kyNang, chuyenCan));
        participation.setCourse(course);
        participation.setLeader(leader(UUID.randomUUID(), UUID.randomUUID(), "HT_XU"));
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);

        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-1")).thenReturn(Optional.of(participation));
        when(certificateRepository.findByLeaderIdAndCourseCode(any(), any())).thenReturn(Optional.empty());
        when(certificateCodeGenerator.buildPrefix(any(), any(), any(), any())).thenReturn("PC-I-2026-");
        when(certificateRepository.findHighestSequenceByCodePrefix("PC-I-2026-")).thenReturn(Optional.of(0L));
        when(certificateCodeGenerator.buildCode(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn("PC-I-2026-1");

        TrainingScoreRequest request = new TrainingScoreRequest();
        ComponentScoreRequest scoreOne = new ComponentScoreRequest();
        scoreOne.setComponentId("component-1");
        scoreOne.setScore(BigDecimal.valueOf(9));
        ComponentScoreRequest scoreTwo = new ComponentScoreRequest();
        scoreTwo.setComponentId("component-2");
        scoreTwo.setScore(BigDecimal.valueOf(6));
        ComponentScoreRequest scoreThree = new ComponentScoreRequest();
        scoreThree.setComponentId("component-3");
        scoreThree.setScore(BigDecimal.ZERO);
        request.setComponentScores(List.of(scoreOne, scoreTwo, scoreThree));
        request.setReview("pass");

        var response = courseParticipationService.score("cp-1", request, authentication("admin-diocese", "training.participation.score.diocese"));

        assertEquals(WorkflowApprovalStatus.PENDING, response.getCertificateApprovalStatus());
        assertEquals(0, BigDecimal.valueOf(8).compareTo(response.getTotalScore()));
        verify(domainEventPublisher).publishAfterCommit(any());
    }

    @Test
    void withdrawShouldBeRejectedForSomeoneElseThanTheSender() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-w", course, "someone-else");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-w")).thenReturn(Optional.of(participation));

        assertThrows(RuntimeException.class, () -> courseParticipationService.withdraw("cp-w", authentication("admin-parish", "training.participation.submit.parish")));
        verify(courseParticipationRepository, never()).delete(any());
    }

    @Test
    void withdrawShouldBeRejectedOnceRegistrationIsApprovedOrRejected() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        for (WorkflowApprovalStatus status : List.of(WorkflowApprovalStatus.APPROVED, WorkflowApprovalStatus.REJECTED)) {
            CourseParticipation participation = pendingParticipation("cp-d", course, "leader-1");
            participation.setApprovalStatus(status);
            when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-d")).thenReturn(Optional.of(participation));

            assertThrows(RuntimeException.class, () -> courseParticipationService.withdraw("cp-d", authentication("admin-parish", "training.participation.submit.parish")));
        }
        verify(courseParticipationRepository, never()).delete(any());
    }

    @Test
    void withdrawShouldDeletePendingRegistrationOfTheSender() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-w", course, "leader-1");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-w")).thenReturn(Optional.of(participation));

        courseParticipationService.withdraw("cp-w", authentication("admin-parish", "training.participation.submit.parish"));

        verify(courseParticipationRepository).delete(participation);
        verify(domainEventPublisher).publishAfterCommit(any());
    }

    @Test
    void bulkApproveShouldApproveValidRegistrationsAndReportTheRest() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-1", course, "leader-9");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-1")).thenReturn(Optional.of(participation));
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("missing")).thenReturn(Optional.empty());
        when(accountScopeValidationHelper.requireActiveDioceseId(any(), any())).thenReturn(course.getDiocese().getId());

        phucuong.ht.modules.training.courseParticipation.model.request.TrainingBulkApprovalRequest request =
                new phucuong.ht.modules.training.courseParticipation.model.request.TrainingBulkApprovalRequest();
        request.setIds(List.of("cp-1", "missing"));
        request.setApproved(Boolean.TRUE);

        var response = courseParticipationService.bulkApprove(request, authentication("admin-diocese", "training.participation.approve.diocese"));

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(WorkflowApprovalStatus.APPROVED, participation.getApprovalStatus());
        assertEquals(phucuong.ht.modules.training.enums.ParticipationStatus.ATTENDED, participation.getParticipationStatus());
    }

    @Test
    void bulkRejectShouldSetRejectedStatuses() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-1", course, "leader-9");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-1")).thenReturn(Optional.of(participation));
        when(accountScopeValidationHelper.requireActiveDioceseId(any(), any())).thenReturn(course.getDiocese().getId());

        phucuong.ht.modules.training.courseParticipation.model.request.TrainingBulkApprovalRequest request =
                new phucuong.ht.modules.training.courseParticipation.model.request.TrainingBulkApprovalRequest();
        request.setIds(List.of("cp-1"));
        request.setApproved(Boolean.FALSE);
        request.setReview("Không đủ điều kiện");

        courseParticipationService.bulkApprove(request, authentication("admin-diocese", "training.participation.approve.diocese"));

        assertEquals(WorkflowApprovalStatus.REJECTED, participation.getApprovalStatus());
        assertEquals(phucuong.ht.modules.training.enums.ParticipationStatus.REJECTED, participation.getParticipationStatus());
    }

    @Test
    void removeParticipantShouldDeleteScoredRegistrationWithoutCertificate() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-r", course, "leader-9");
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setTotalScore(BigDecimal.valueOf(8));
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-r")).thenReturn(Optional.of(participation));
        when(certificateRepository.findByLeaderIdAndCourseCode(any(), any())).thenReturn(Optional.empty());
        when(accountScopeValidationHelper.requireActiveDioceseId(any(), any())).thenReturn(course.getDiocese().getId());

        courseParticipationService.removeParticipant("cp-r", authentication("admin-diocese", "training.participation.approve.diocese"));

        verify(courseParticipationRepository).delete(participation);
    }

    @Test
    void removeParticipantShouldBeRejectedWhenCertificateIsApproved() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-r", course, "leader-9");
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-r")).thenReturn(Optional.of(participation));
        when(accountScopeValidationHelper.requireActiveDioceseId(any(), any())).thenReturn(course.getDiocese().getId());

        assertThrows(RuntimeException.class, () -> courseParticipationService.removeParticipant("cp-r", authentication("admin-diocese", "training.participation.approve.diocese")));
        verify(courseParticipationRepository, never()).delete(any());
    }

    @Test
    void removeParticipantShouldBeRejectedOutsideApproverScope() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-r", course, "leader-9");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-r")).thenReturn(Optional.of(participation));
        when(accountScopeValidationHelper.requireActiveDioceseId(any(), any())).thenReturn(UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> courseParticipationService.removeParticipant("cp-r", authentication("admin-diocese", "training.participation.approve.diocese")));
        verify(courseParticipationRepository, never()).delete(any());
    }

    private CourseParticipation pendingParticipation(String id, TrainingCourse course, String createdBy) {
        CourseParticipation participation = new CourseParticipation();
        participation.setId(id);
        participation.setCourse(course);
        participation.setLeader(leader(UUID.randomUUID(), UUID.randomUUID(), "HT_XU"));
        participation.setCreatedBy(createdBy);
        participation.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        participation.setParticipationStatus(phucuong.ht.modules.training.enums.ParticipationStatus.REGISTERED);
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
        return participation;
    }

    @Test
    void rescoringShouldUpdateExistingComponentScoresInPlace() {
        courseParticipationService = buildService();
        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-re");
        TrainingCourse course = course("SMKN_I", UnitType.DIOCESE);
        course.setCourseName("SMKN I");
        course.setHostYear(2026);
        course.setPassingScore(BigDecimal.valueOf(8));
        ScoreComponentTemplate first = component("component-1", "TINH_THAN", BigDecimal.valueOf(10), 0, BigDecimal.ONE, true);
        ScoreComponentTemplate second = component("component-2", "KY_NANG", BigDecimal.valueOf(10), 1, BigDecimal.ONE, true);
        when(scoreComponentTemplateRepository.findActiveForCourse(org.mockito.ArgumentMatchers.eq("SMKN_I"), any(), any()))
                .thenReturn(List.of(first, second));
        participation.setCourse(course);
        participation.setLeader(leader(UUID.randomUUID(), UUID.randomUUID(), "HT_XU"));
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-re")).thenReturn(Optional.of(participation));
        when(certificateRepository.findByLeaderIdAndCourseCode(any(), any())).thenReturn(Optional.empty());
        when(certificateCodeGenerator.buildPrefix(any(), any(), any(), any())).thenReturn("PC-I-2026-");
        when(certificateRepository.findHighestSequenceByCodePrefix("PC-I-2026-")).thenReturn(Optional.of(0L));
        when(certificateCodeGenerator.buildCode(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn("PC-I-2026-1");

        courseParticipationService.score("cp-re", scoreRequest(5, 5), authentication("admin-diocese", "training.participation.score.diocese"));
        var firstRows = List.copyOf(participation.getComponentScores());
        courseParticipationService.score("cp-re", scoreRequest(9, 9), authentication("admin-diocese", "training.participation.score.diocese"));

        // Cùng các đối tượng dòng điểm được cập nhật, không thêm/xóa dòng nào (tránh vi phạm ràng buộc duy nhất).
        assertEquals(2, participation.getComponentScores().size());
        assertEquals(firstRows.get(0), participation.getComponentScores().get(0));
        assertEquals(0, BigDecimal.valueOf(9).compareTo(participation.getComponentScores().get(0).getScore()));
        assertEquals(0, BigDecimal.valueOf(9).compareTo(participation.getTotalScore()));
    }

    private TrainingScoreRequest scoreRequest(int firstScore, int secondScore) {
        TrainingScoreRequest request = new TrainingScoreRequest();
        ComponentScoreRequest one = new ComponentScoreRequest();
        one.setComponentId("component-1");
        one.setScore(BigDecimal.valueOf(firstScore));
        ComponentScoreRequest two = new ComponentScoreRequest();
        two.setComponentId("component-2");
        two.setScore(BigDecimal.valueOf(secondScore));
        request.setComponentScores(List.of(one, two));
        return request;
    }

    @Test
    void scoreNoteCanBeSavedWhileScoresAreLocked() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        course.setScoreLocked(Boolean.TRUE);
        CourseParticipation participation = pendingParticipation("cp-n", course, "leader-9");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-n")).thenReturn(Optional.of(participation));
        var request = new phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreNoteRequest();
        request.setNote("  Vắng buổi cuối  ");

        var response = courseParticipationService.updateScoreNote("cp-n", request, authentication("admin-diocese", "training.participation.score.diocese"));

        assertEquals("Vắng buổi cuối", response.getScoreNote());
    }

    @Test
    void scoreNoteRejectedWhenCourseFinished() {
        courseParticipationService = buildService();
        TrainingCourse finished = course("SMTL_I", UnitType.DIOCESE);
        finished.setFinishedAt(java.time.OffsetDateTime.now());
        CourseParticipation participation = pendingParticipation("cp-f", finished, "leader-9");
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-f")).thenReturn(Optional.of(participation));
        var request = new phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreNoteRequest();
        request.setNote("x");

        assertThrows(RuntimeException.class, () -> courseParticipationService.updateScoreNote("cp-f", request, authentication("admin-diocese", "training.participation.score.diocese")));
    }

    @Test
    void prerequisiteYearsAreCountedFromTheLatestAchievedYear() throws Exception {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_II", UnitType.DIOCESE);
        course.setHostYear(2026);
        course.setYearsActive(2);
        phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType prerequisite = new phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType();
        prerequisite.setTypeCode("SMTL_I");
        course.getCourseType().setPrerequisiteType(prerequisite);
        phucuong.ht.modules.leader.model.entity.Leader leader = new phucuong.ht.modules.leader.model.entity.Leader();
        leader.setId(java.util.UUID.randomUUID());
        var method = CourseParticipationService.class.getDeclaredMethod("isEligible", phucuong.ht.modules.leader.model.entity.Leader.class, TrainingCourse.class);
        method.setAccessible(true);

        // Lần đạt gần nhất là 2024: 2026 - 2024 = 2 >= 2 nên đủ điều kiện.
        when(courseParticipationRepository.findLatestCompletedYearByLeaderAndCourseType(leader.getId(), "SMTL_I")).thenReturn(Optional.of(2024));
        org.junit.jupiter.api.Assertions.assertTrue((Boolean) method.invoke(courseParticipationService, leader, course));

        // Lần đạt gần nhất là 2025 (dù trước đó đạt từ 2020): 2026 - 2025 = 1 < 2 nên chưa đủ.
        when(courseParticipationRepository.findLatestCompletedYearByLeaderAndCourseType(leader.getId(), "SMTL_I")).thenReturn(Optional.of(2025));
        org.junit.jupiter.api.Assertions.assertFalse((Boolean) method.invoke(courseParticipationService, leader, course));
    }

    @Test
    void scoreShouldRejectWhenScoreEditingIsLocked() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        course.setScoreLocked(Boolean.TRUE);
        CourseParticipation participation = pendingParticipation("cp-l", course, "leader-9");
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-l")).thenReturn(Optional.of(participation));

        assertThrows(RuntimeException.class, () -> courseParticipationService.score("cp-l", scoreRequest(9, 9), authentication("admin-diocese", "training.participation.score.diocese")));
        verify(scoreComponentTemplateRepository, never()).findActiveForCourse(any(), any(), any());
    }

    @Test
    void scoreShouldRejectWhenApprovedCertificateExists() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        CourseParticipation participation = pendingParticipation("cp-c", course, "leader-9");
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        phucuong.ht.modules.certificate.model.entity.Certificate certificate = new phucuong.ht.modules.certificate.model.entity.Certificate();
        certificate.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-c")).thenReturn(Optional.of(participation));
        when(certificateRepository.findByLeaderIdAndCourseCode(any(), any())).thenReturn(Optional.of(certificate));

        assertThrows(RuntimeException.class, () -> courseParticipationService.score("cp-c", scoreRequest(9, 9), authentication("admin-diocese", "training.participation.score.diocese")));
        verify(scoreComponentTemplateRepository, never()).findActiveForCourse(any(), any(), any());
    }

    @Test
    void rescoringBelowPassingScoreShouldDeletePendingCertificate() {
        courseParticipationService = buildService();
        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-d");
        TrainingCourse course = course("SMKN_I", UnitType.DIOCESE);
        course.setPassingScore(BigDecimal.valueOf(8));
        ScoreComponentTemplate first = component("component-1", "TINH_THAN", BigDecimal.valueOf(10), 0, BigDecimal.ONE, true);
        ScoreComponentTemplate second = component("component-2", "KY_NANG", BigDecimal.valueOf(10), 1, BigDecimal.ONE, true);
        when(scoreComponentTemplateRepository.findActiveForCourse(org.mockito.ArgumentMatchers.eq("SMKN_I"), any(), any()))
                .thenReturn(List.of(first, second));
        participation.setCourse(course);
        participation.setLeader(leader(UUID.randomUUID(), UUID.randomUUID(), "HT_XU"));
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
        phucuong.ht.modules.certificate.model.entity.Certificate pending = new phucuong.ht.modules.certificate.model.entity.Certificate();
        pending.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-d")).thenReturn(Optional.of(participation));
        when(certificateRepository.findByLeaderIdAndCourseCode(any(), any())).thenReturn(Optional.of(pending));

        courseParticipationService.score("cp-d", scoreRequest(3, 3), authentication("admin-diocese", "training.participation.score.diocese"));

        verify(certificateRepository).delete(pending);
        assertEquals(WorkflowApprovalStatus.NOT_REQUIRED, participation.getCertificateApprovalStatus());
    }

    @Test
    void scoreShouldRejectWhenCourseIsNotOngoing() {
        courseParticipationService = buildService();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        course.setStartDate(java.time.LocalDate.now().plusDays(5));
        course.setEndDate(java.time.LocalDate.now().plusDays(7));
        CourseParticipation participation = pendingParticipation("cp-s", course, "leader-9");
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-s")).thenReturn(Optional.of(participation));

        TrainingScoreRequest request = new TrainingScoreRequest();
        ComponentScoreRequest score = new ComponentScoreRequest();
        score.setComponentId("component-1");
        score.setScore(BigDecimal.ONE);
        request.setComponentScores(List.of(score));

        assertThrows(RuntimeException.class, () -> courseParticipationService.score("cp-s", request, authentication("admin-diocese", "training.participation.score.diocese")));
        verify(scoreComponentTemplateRepository, never()).findActiveForCourse(any(), any(), any());
    }

    @Test
    void scoreShouldRejectWhenCertificateAlreadyApproved() {
        courseParticipationService = buildService();
        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-approved");
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-approved"))
                .thenReturn(Optional.of(participation));

        assertThrows(RuntimeException.class, () -> courseParticipationService.score(
                "cp-approved",
                new TrainingScoreRequest(),
                authentication("admin-diocese", "training.participation.score.diocese")
        ));
        verify(domainEventPublisher, never()).publishAfterCommit(any());
    }

    private CourseParticipationService buildService() {
        return new CourseParticipationService(
                courseParticipationRepository,
                trainingCourseRepository,
                leaderRepository,
                parishRepository,
                deaneryRepository,
                new TrainingWorkflowSupport(),
                scoreComponentTemplateRepository,
                accountScopeValidationHelper,
                commonValidationHelper,
                certificateRepository,
                certificateCodeGenerator,
                domainEventPublisher,
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier.class),
                trainingNotificationRecipientResolver,
                trainingNotificationService,
                org.mockito.Mockito.mock(phucuong.ht.modules.leaderLevel.service.LeaderLevelService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.training.CourseActivityGuard.class)
        );
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String permission) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, "leader-1", List.of("ADMIN_PARISH"), List.of(permission));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private TrainingCourse course(String typeCode, UnitType managementUnitType) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(typeCode);
        type.setTypeName(typeCode);
        type.setManagementUnitType(managementUnitType);
        // Khóa có cấp độ đạt được nên chấm đạt sẽ sinh chứng nhận.
        type.setResultingLevel("HT_I");
        type.setStatus(Boolean.TRUE);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("COURSE");
        course.setCourseType(type);
        // Khóa đang diễn ra (cho phép chấm điểm).
        course.setStartDate(java.time.LocalDate.now().minusDays(1));
        course.setEndDate(java.time.LocalDate.now().plusDays(1));
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName("Diocese A");
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setName("Deanery A");
        deanery.setDiocese(diocese);
        course.setDiocese(diocese);
        course.setDeanery(deanery);
        course.setStatus(Boolean.TRUE);
        course.setRegistrationStartAt(OffsetDateTime.now().minusDays(1));
        course.setRegistrationEndAt(OffsetDateTime.now().plusDays(1));
        return course;
    }

    private Leader leader(UUID leaderId, UUID parishId, String level) {
        Leader leader = new Leader();
        leader.setId(leaderId);
        leader.setLeaderLevel(level);
        leader.setStatus(Boolean.TRUE);
        leader.setBirthDate(LocalDate.now().minusYears(20));
        leader.setCreatedAt(OffsetDateTime.now().minusYears(5));
        Parish parish = new Parish();
        parish.setId(parishId);
        leader.setParish(parish);
        return leader;
    }

    private Parish parish(UUID parishId, TrainingCourse course) {
        Parish parish = new Parish();
        parish.setId(parishId);
        parish.setName("Parish A");
        parish.setDeanery(course.getDeanery());
        return parish;
    }

    private ScoreComponentTemplate component(String id, String code, BigDecimal maxScore, int displayOrder, BigDecimal coefficient, boolean includeInFinal) {
        ScoreComponentTemplate component = new ScoreComponentTemplate();
        component.setId(id);
        component.setCode(code);
        component.setName(code);
        component.setMaxScore(maxScore);
        component.setDisplayOrder(displayOrder);
        component.setCoefficient(coefficient);
        component.setIncludeInFinal(includeInFinal);
        return component;
    }
}
