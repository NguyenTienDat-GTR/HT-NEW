package phucuong.ht.modules.certificate.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateUpdateRequest;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private TrainingCourseRepository trainingCourseRepository;

    @Mock
    private LeaderRepository leaderRepository;

    @Mock
    private CourseParticipationRepository courseParticipationRepository;

    @Mock
    private RankHistoryRepository rankHistoryRepository;

    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(new securityUtils());

    @InjectMocks
    private CertificateService certificateService;

    @Test
    void approveShouldPromoteLeaderWhenPassingCourse() {
        certificateService = service();

        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setLeaderLevel(LeaderLevel.HT_XU);

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("SMKNI");
        course.setCourseType(CourseType.SMKN_I);
        ScoreFormulaTemplate formula = new ScoreFormulaTemplate();
        formula.setPassingScore(BigDecimal.valueOf(5));
        course.setScoreFormula(formula);

        Certificate certificate = new Certificate();
        certificate.setCertificateCode("CERT-1");
        certificate.setLeader(leader);
        certificate.setCourse(course);

        CourseParticipation participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setTotalScore(BigDecimal.valueOf(7));
        participation.setResult(CourseResult.COMPLETED);
        participation.setDioceseApprovalStatus(WorkflowApprovalStatus.APPROVED);

        when(certificateRepository.findByCodeWithLeaderAndCourse("CERT-1")).thenReturn(Optional.of(certificate));
        when(courseParticipationRepository.findByLeaderIdAndCourseCode(leader.getId(), "SMKNI")).thenReturn(Optional.of(participation));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin-diocese", "leader-1", List.of("ADMIN_DIOCESE"), List.of("training.certificate.approve.diocese"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        var response = certificateService.approve("CERT-1", new CertificateApproveRequest(), authentication);

        assertEquals(WorkflowApprovalStatus.APPROVED, response.getApprovalStatus());
        assertEquals(LeaderLevel.HT_I, leader.getLeaderLevel());
        verify(rankHistoryRepository).save(any());
        verify(domainEventPublisher).publishAfterCommit(any());
    }

    @Test
    void updateShouldRejectDeaneryScopedReassignmentToAnotherDeanery() {
        certificateService = service();
        UUID sourceDeaneryId = UUID.randomUUID();
        UUID targetDeaneryId = UUID.randomUUID();
        Leader sourceLeader = leaderInDeanery(sourceDeaneryId);
        Certificate certificate = certificateInCourse("CERT-1", deaneryCourse("SOURCE", sourceDeaneryId), sourceLeader);
        Leader targetLeader = leaderInDeanery(targetDeaneryId);
        TrainingCourse targetCourse = deaneryCourse("TARGET", targetDeaneryId);
        var authentication = deaneryAuthentication();

        CertificateUpdateRequest request = new CertificateUpdateRequest();
        request.setLeaderId(targetLeader.getId());
        request.setCourseCode(targetCourse.getCourseCode());

        when(certificateRepository.findByCodeWithLeaderAndCourse("CERT-1")).thenReturn(Optional.of(certificate));
        when(leaderRepository.findByIdWithParish(targetLeader.getId())).thenReturn(Optional.of(targetLeader));
        when(trainingCourseRepository.findByCodeWithRequirement("TARGET")).thenReturn(Optional.of(targetCourse));
        when(accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.certificate.update.deanery"))
                .thenReturn(sourceDeaneryId);

        ApiException exception = assertThrows(ApiException.class,
                () -> certificateService.update("CERT-1", request, authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("SOURCE", certificate.getCourse().getCourseCode());
        assertSame(sourceLeader, certificate.getLeader());
    }

    @Test
    void updateShouldRejectDioceseScopedReassignmentToAnotherDiocese() {
        certificateService = service();
        UUID sourceDioceseId = UUID.randomUUID();
        UUID targetDioceseId = UUID.randomUUID();
        Leader sourceLeader = leaderInDiocese(sourceDioceseId);
        Certificate certificate = certificateInCourse("CERT-1", dioceseCourse("SOURCE", sourceDioceseId), sourceLeader);
        Leader targetLeader = leaderInDiocese(targetDioceseId);
        TrainingCourse targetCourse = dioceseCourse("TARGET", targetDioceseId);
        var authentication = dioceseAuthentication();

        CertificateUpdateRequest request = new CertificateUpdateRequest();
        request.setLeaderId(targetLeader.getId());
        request.setCourseCode(targetCourse.getCourseCode());

        when(certificateRepository.findByCodeWithLeaderAndCourse("CERT-1")).thenReturn(Optional.of(certificate));
        when(leaderRepository.findByIdWithParish(targetLeader.getId())).thenReturn(Optional.of(targetLeader));
        when(trainingCourseRepository.findByCodeWithRequirement("TARGET")).thenReturn(Optional.of(targetCourse));
        when(accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.certificate.update.diocese"))
                .thenReturn(sourceDioceseId);

        ApiException exception = assertThrows(ApiException.class,
                () -> certificateService.update("CERT-1", request, authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("SOURCE", certificate.getCourse().getCourseCode());
        assertSame(sourceLeader, certificate.getLeader());
    }

    @Test
    void updateShouldAllowReassignmentWithinTheTargetDeaneryScope() {
        certificateService = service();
        UUID deaneryId = UUID.randomUUID();
        Certificate certificate = certificateInCourse("CERT-1", deaneryCourse("SOURCE", deaneryId), leaderInDeanery(deaneryId));
        Leader targetLeader = leaderInDeanery(deaneryId);
        TrainingCourse targetCourse = deaneryCourse("TARGET", deaneryId);
        var authentication = deaneryAuthentication();

        CertificateUpdateRequest request = new CertificateUpdateRequest();
        request.setLeaderId(targetLeader.getId());
        request.setCourseCode(targetCourse.getCourseCode());

        when(certificateRepository.findByCodeWithLeaderAndCourse("CERT-1")).thenReturn(Optional.of(certificate));
        when(leaderRepository.findByIdWithParish(targetLeader.getId())).thenReturn(Optional.of(targetLeader));
        when(trainingCourseRepository.findByCodeWithRequirement("TARGET")).thenReturn(Optional.of(targetCourse));
        when(accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.certificate.update.deanery"))
                .thenReturn(deaneryId);

        var response = certificateService.update("CERT-1", request, authentication);

        assertEquals(targetLeader.getId(), response.getLeaderId());
        assertEquals("TARGET", response.getCourseCode());
        assertSame(targetLeader, certificate.getLeader());
        assertSame(targetCourse, certificate.getCourse());
    }

    private CertificateService service() {
        return new CertificateService(
                certificateRepository,
                trainingCourseRepository,
                leaderRepository,
                courseParticipationRepository,
                rankHistoryRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                commonValidationHelper,
                domainEventPublisher
        );
    }

    private Certificate certificateInCourse(String certificateCode, TrainingCourse course, Leader leader) {
        Certificate certificate = new Certificate();
        certificate.setCertificateCode(certificateCode);
        certificate.setCourse(course);
        certificate.setLeader(leader);
        return certificate;
    }

    private TrainingCourse deaneryCourse(String courseCode, UUID deaneryId) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setDiocese(diocese);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(courseCode);
        course.setCourseType(CourseType.SMTL_DT);
        course.setDeanery(deanery);
        return course;
    }

    private TrainingCourse dioceseCourse(String courseCode, UUID dioceseId) {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(courseCode);
        course.setCourseType(CourseType.SMKN_I);
        course.setDiocese(diocese);
        return course;
    }

    private Leader leaderInDeanery(UUID deaneryId) {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        deanery.setDiocese(diocese);
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setDeanery(deanery);
        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setParish(parish);
        return leader;
    }

    private Leader leaderInDiocese(UUID dioceseId) {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setDiocese(diocese);
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setDeanery(deanery);
        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setParish(parish);
        return leader;
    }

    private UsernamePasswordAuthenticationToken deaneryAuthentication() {
        return authentication("admin-deanery", "training.certificate.update.deanery");
    }

    private UsernamePasswordAuthenticationToken dioceseAuthentication() {
        return authentication("admin-diocese", "training.certificate.update.diocese");
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String authority) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                username,
                "leader-1",
                List.of("ADMIN"),
                List.of(authority)
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
