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
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.TrainingScoreFormulaEvaluator;
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
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaComponent;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;
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

    private final TrainingScoreFormulaEvaluator trainingScoreFormulaEvaluator = new TrainingScoreFormulaEvaluator(new CommonValidationHelper(new securityUtils()));
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
        Leader leader = leader(leaderId, parishId, LeaderLevel.HT_XU);
        course.getCourseType().setRequiredCurrentLevel(LeaderLevel.DU_TRUONG);

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
        Leader leader = leader(leaderId, parishId, LeaderLevel.HT_XU);
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
        Leader leader = leader(leaderId, parishId, LeaderLevel.HT_XU);
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
    void registerShouldReturnBatchSummaryForDioceseCourseWithoutAutoApprovalAtParishStep() {
        courseParticipationService = buildService();

        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TrainingCourse course = course("SMTL_I", UnitType.DIOCESE);
        Leader leader = leader(leaderId, parishId, LeaderLevel.HT_XU);
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
        TrainingCourse course = course("SMKN_I", UnitType.DIOCESE);
        course.setCourseName("SMKN I");
        course.setHostYear(2026);
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName("Phu Cuong");
        course.setDiocese(diocese);
        ScoreFormulaTemplate formula = new ScoreFormulaTemplate();
        formula.setId("formula-1");
        formula.setExpression("TINH_THAN + KY_NANG");
        formula.setPassingScore(BigDecimal.valueOf(8));
        ScoreComponentTemplate tinhThan = component("component-1", "TINH_THAN", BigDecimal.valueOf(10), 0);
        ScoreComponentTemplate kyNang = component("component-2", "KY_NANG", BigDecimal.valueOf(10), 1);
        formula.setComponents(List.of(link(formula, tinhThan, 0), link(formula, kyNang, 1)));
        course.setScoreFormula(formula);
        participation.setCourse(course);
        participation.setLeader(leader(UUID.randomUUID(), UUID.randomUUID(), LeaderLevel.HT_XU));
        participation.setDioceseApprovalStatus(WorkflowApprovalStatus.APPROVED);

        when(courseParticipationRepository.findByIdWithLeaderAndCourse("cp-1")).thenReturn(Optional.of(participation));
        when(certificateRepository.findByLeaderIdAndCourseCode(any(), any())).thenReturn(Optional.empty());
        when(certificateCodeGenerator.buildPrefix(any(), any(), any())).thenReturn("PC-I-2026-");
        when(certificateRepository.findHighestSequenceByCodePrefix("PC-I-2026-")).thenReturn(Optional.of(0L));
        when(certificateCodeGenerator.buildCode(any(), any(), any(), any(Long.class))).thenReturn("PC-I-2026-1");

        TrainingScoreRequest request = new TrainingScoreRequest();
        ComponentScoreRequest scoreOne = new ComponentScoreRequest();
        scoreOne.setComponentId("component-1");
        scoreOne.setScore(BigDecimal.valueOf(3));
        ComponentScoreRequest scoreTwo = new ComponentScoreRequest();
        scoreTwo.setComponentId("component-2");
        scoreTwo.setScore(BigDecimal.valueOf(5));
        request.setComponentScores(List.of(scoreOne, scoreTwo));
        request.setReview("pass");

        var response = courseParticipationService.score("cp-1", request, authentication("admin-diocese", "training.participation.score.diocese"));

        assertEquals(WorkflowApprovalStatus.PENDING, response.getCertificateApprovalStatus());
        assertEquals(BigDecimal.valueOf(8), response.getTotalScore());
        verify(domainEventPublisher).publishAfterCommit(any());
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
                trainingScoreFormulaEvaluator,
                accountScopeValidationHelper,
                commonValidationHelper,
                certificateRepository,
                certificateCodeGenerator,
                domainEventPublisher,
                trainingNotificationRecipientResolver,
                trainingNotificationService
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
        type.setStatus(Boolean.TRUE);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("COURSE");
        course.setCourseType(type);
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

    private Leader leader(UUID leaderId, UUID parishId, LeaderLevel level) {
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

    private ScoreComponentTemplate component(String id, String code, BigDecimal maxScore, int displayOrder) {
        ScoreComponentTemplate component = new ScoreComponentTemplate();
        component.setId(id);
        component.setCode(code);
        component.setName(code);
        component.setMaxScore(maxScore);
        component.setDisplayOrder(displayOrder);
        return component;
    }

    private ScoreFormulaComponent link(ScoreFormulaTemplate formula, ScoreComponentTemplate component, int displayOrder) {
        ScoreFormulaComponent link = new ScoreFormulaComponent();
        link.setFormula(formula);
        link.setComponent(component);
        link.setDisplayOrder(displayOrder);
        return link;
    }
}
