package phucuong.ht.modules.certificate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.certificate.security.CertificateAccessEvaluator;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.rankHistory.service.RankPromotionService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.service.CourseFinishService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateRevokeTest {

    private CertificateRepository certificateRepository;
    private CourseParticipationRepository participationRepository;
    private DomainEventPublisher eventPublisher;
    private RankPromotionService rankPromotionService;
    private CertificateService service;
    private Certificate certificate;
    private CourseParticipation participation;
    private final Authentication admin = adminAuthentication();

    @BeforeEach
    void setUp() {
        certificateRepository = mock(CertificateRepository.class);
        participationRepository = mock(CourseParticipationRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        rankPromotionService = mock(RankPromotionService.class);
        service = new CertificateService(
                certificateRepository, mock(TrainingCourseRepository.class), mock(LeaderRepository.class),
                participationRepository, rankPromotionService, new TrainingWorkflowSupport(),
                mock(AccountScopeValidationHelper.class), new CommonValidationHelper(new securityUtils()), eventPublisher,
                mock(CertificateRealtimeNotifier.class), mock(CertificateAccessEvaluator.class), mock(CourseFinishService.class));

        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("K1");
        course.setPassingScore(BigDecimal.valueOf(5));

        certificate = new Certificate();
        certificate.setCertificateCode("CERT-1");
        certificate.setLeader(leader);
        certificate.setCourse(course);
        certificate.setApprovalStatus(WorkflowApprovalStatus.APPROVED);

        participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setTotalScore(BigDecimal.valueOf(9));
        participation.setResult(CourseResult.COMPLETED);
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setFinalResult(CourseFinalResult.PASSED);

        when(certificateRepository.findByCodeWithLeaderAndCourse("CERT-1")).thenReturn(Optional.of(certificate));
        when(participationRepository.findByLeaderIdAndCourseCode(leader.getId(), "K1")).thenReturn(Optional.of(participation));
    }

    private static Authentication adminAuthentication() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-diocese", "leader-1", List.of("ADMIN_DIOCESE"), List.of("training.certificate.revoke.diocese"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private CertificateRevokeRequest reason(String text) {
        CertificateRevokeRequest request = new CertificateRevokeRequest();
        request.setReason(text);
        return request;
    }

    @Test
    void revokeFailsTheParticipationEvenWhenScoreIsAbovePassingScore() {
        CertificateResponse response = service.revoke("CERT-1", reason("Sai thông tin"), admin);

        assertEquals(WorkflowApprovalStatus.REVOKED, response.getApprovalStatus());
        assertEquals("Sai thông tin", certificate.getRevocationReason());
        assertNotNull(certificate.getRevokedAt());
        assertEquals("leader-1", certificate.getRevokedBy());
        assertEquals(WorkflowApprovalStatus.REVOKED, participation.getCertificateApprovalStatus());
        assertEquals(CourseFinalResult.FAILED, participation.getFinalResult());
        // Điểm vẫn cao hơn điểm đạt; chỉ kết quả chung của hồ sơ bị chuyển thành không đạt.
        assertEquals(CourseResult.COMPLETED, participation.getResult());
        verify(eventPublisher).publishAfterCommit(any());
        // Hạ cấp về cấp trước đó huynh trưởng đã đạt nhờ lịch sử cấp bậc của khóa.
        verify(rankPromotionService).revertPromotion(eq(certificate.getLeader()), eq("K1"), any(), eq("leader-1"), any());
    }

    @Test
    void revokeRequiresAReason() {
        assertThrows(ApiException.class, () -> service.revoke("CERT-1", reason("  "), admin));
        assertThrows(ApiException.class, () -> service.revoke("CERT-1", null, admin));
        assertEquals(WorkflowApprovalStatus.APPROVED, certificate.getApprovalStatus());
    }

    @Test
    void onlyApprovedCertificatesCanBeRevoked() {
        certificate.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        assertThrows(ApiException.class, () -> service.revoke("CERT-1", reason("x"), admin));
        certificate.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        assertThrows(ApiException.class, () -> service.revoke("CERT-1", reason("x"), admin));
    }

    @Test
    void revokingTwiceIsRejected() {
        service.revoke("CERT-1", reason("lần 1"), admin);
        assertThrows(ApiException.class, () -> service.revoke("CERT-1", reason("lần 2"), admin));
    }

    @Test
    void revokedCertificateCannotBeApprovedAgain() {
        service.revoke("CERT-1", reason("Sai thông tin"), admin);

        ApiException error = assertThrows(ApiException.class, () -> service.approve("CERT-1", new CertificateApproveRequest(), admin));

        assertEquals(WorkflowApprovalStatus.REVOKED, certificate.getApprovalStatus());
        assertEquals(true, error.getMessage().contains("thu hồi"));
    }
}
