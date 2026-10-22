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
import phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateReissueRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.certificate.security.CertificateAccessEvaluator;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService.LevelOrder;
import phucuong.ht.modules.rankHistory.service.RankPromotionService;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.service.CourseFinishService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateRevokeTest {

    private CertificateRepository certificateRepository;
    private CourseParticipationRepository participationRepository;
    private DomainEventPublisher eventPublisher;
    private RankPromotionService rankPromotionService;
    private CourseFinishService courseFinishService;
    private CertificateService service;
    private Certificate certificate;
    private CourseParticipation participation;
    private Leader leader;
    private TrainingCourse course;
    private final Authentication admin = adminAuthentication();

    @BeforeEach
    void setUp() {
        certificateRepository = mock(CertificateRepository.class);
        participationRepository = mock(CourseParticipationRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        rankPromotionService = mock(RankPromotionService.class);
        courseFinishService = mock(CourseFinishService.class);
        CommonValidationHelper validationHelper = new CommonValidationHelper(new securityUtils());
        LeaderLevelService leaderLevelService = mock(LeaderLevelService.class);
        when(leaderLevelService.order(any())).thenReturn(new LevelOrder(
                Map.of("NONE", 0, "DU_TRUONG", 1, "HT_I", 2, "HT_II", 3, "HT_II_B", 3),
                Map.of("NONE", "Chưa có cấp", "DU_TRUONG", "Dự trưởng", "HT_I", "Huynh trưởng cấp I", "HT_II", "Huynh trưởng cấp II",
                        "HT_II_B", "Huynh trưởng cấp II (nhánh B)")));
        CertificateLifecycleService lifecycle = new CertificateLifecycleService(
                certificateRepository, participationRepository, rankPromotionService, courseFinishService, leaderLevelService,
                new TrainingWorkflowSupport(), validationHelper, eventPublisher, mock(CertificateRealtimeNotifier.class));
        service = new CertificateService(
                certificateRepository, mock(TrainingCourseRepository.class), mock(LeaderRepository.class),
                participationRepository, lifecycle, new CourseActivityGuard(validationHelper), new TrainingWorkflowSupport(),
                mock(AccountScopeValidationHelper.class), validationHelper, eventPublisher,
                mock(CertificateRealtimeNotifier.class), mock(CertificateAccessEvaluator.class), courseFinishService, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));

        leader = new Leader();
        leader.setId(UUID.randomUUID());
        course = course("K1", "HT_I");
        certificate = certificate("CERT-1", course, WorkflowApprovalStatus.APPROVED);
        participation = participation(course, "HT_I");
        register(certificate, participation);
    }

    private TrainingCourse course(String code, String resultingLevel) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("T-" + code);
        type.setResultingLevel(resultingLevel);
        TrainingCourse created = new TrainingCourse();
        created.setCourseCode(code);
        created.setCourseName("Khóa " + code);
        created.setCourseType(type);
        created.setDiocese(diocese);
        created.setPassingScore(BigDecimal.valueOf(5));
        created.setStatus(Boolean.TRUE);
        return created;
    }

    private Certificate certificate(String code, TrainingCourse of, WorkflowApprovalStatus status) {
        Certificate created = new Certificate();
        created.setCertificateCode(code);
        created.setLeader(leader);
        created.setCourse(of);
        created.setApprovalStatus(status);
        return created;
    }

    private CourseParticipation participation(TrainingCourse of, String level) {
        CourseParticipation created = new CourseParticipation();
        created.setLeader(leader);
        created.setCourse(of);
        created.setTotalScore(BigDecimal.valueOf(9));
        created.setResult(CourseResult.COMPLETED);
        created.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        created.setFinalResult(CourseFinalResult.PASSED);
        created.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        return created;
    }

    private void register(Certificate cert, CourseParticipation part) {
        when(certificateRepository.findByCodeWithLeaderAndCourse(cert.getCertificateCode())).thenReturn(Optional.of(cert));
        when(participationRepository.findByLeaderIdAndCourseCode(leader.getId(), cert.getCourse().getCourseCode())).thenReturn(Optional.of(part));
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

    private CertificateReissueRequest reissueReason(String text) {
        CertificateReissueRequest request = new CertificateReissueRequest();
        request.setReason(text);
        return request;
    }

    // ---------------------------------------------------------------- thu hồi

    @Test
    void revokeFailsTheParticipationEvenWhenScoreIsAbovePassingScore() {
        CertificateResponse response = service.revoke("CERT-1", reason("Sai thông tin"), admin);

        assertEquals(WorkflowApprovalStatus.REVOKED, response.getApprovalStatus());
        assertEquals("Sai thông tin", certificate.getRevocationReason());
        assertEquals(CertificateRevocationSource.MANUAL, certificate.getRevocationSource());
        assertNotNull(certificate.getRevokedAt());
        assertEquals("leader-1", certificate.getRevokedBy());
        assertEquals(WorkflowApprovalStatus.REVOKED, participation.getCertificateApprovalStatus());
        assertEquals(CourseFinalResult.FAILED, participation.getFinalResult());
        // Điểm vẫn cao hơn điểm đạt; chỉ kết quả chung của hồ sơ bị chuyển thành không đạt.
        assertEquals(CourseResult.COMPLETED, participation.getResult());
        verify(eventPublisher).publishAfterCommit(any());
        // Hạ cấp về cấp trước đó huynh trưởng đã đạt nhờ lịch sử cấp bậc của khóa.
        verify(rankPromotionService).revertPromotion(eq(leader), eq("K1"), any(), eq("leader-1"), any());
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
        assertTrue(error.getMessage().contains("thu hồi"));
    }

    @Test
    void revokeIsBlockedWhileHigherLevelCertificateIsStillActive() {
        // Huynh trưởng đang giữ chứng nhận HT cấp II (cao hơn) từ khóa khác: phải thu hồi cấp cao nhất trước.
        TrainingCourse higherCourse = course("K2", "HT_II");
        Certificate higher = certificate("CERT-2", higherCourse, WorkflowApprovalStatus.APPROVED);
        register(higher, participation(higherCourse, "HT_II"));
        when(certificateRepository.findOthersByLeader(leader.getId(), "CERT-1")).thenReturn(List.of(higher));

        ApiException error = assertThrows(ApiException.class, () -> service.revoke("CERT-1", reason("x"), admin));

        assertTrue(error.getMessage().contains("cấp cao nhất"));
        assertEquals(WorkflowApprovalStatus.APPROVED, certificate.getApprovalStatus());
        verify(rankPromotionService, never()).revertPromotion(any(), any(), any(), any(), any());
    }

    @Test
    void revokingTheHighestLevelFirstIsAllowed() {
        TrainingCourse higherCourse = course("K2", "HT_II");
        Certificate higher = certificate("CERT-2", higherCourse, WorkflowApprovalStatus.APPROVED);
        register(higher, participation(higherCourse, "HT_II"));
        when(certificateRepository.findOthersByLeader(leader.getId(), "CERT-2")).thenReturn(List.of(certificate));

        service.revoke("CERT-2", reason("Sai thông tin"), admin);

        assertEquals(WorkflowApprovalStatus.REVOKED, higher.getApprovalStatus());
        // Sau đó mới thu hồi được chứng nhận cấp thấp hơn.
        higher.setApprovalStatus(WorkflowApprovalStatus.REVOKED);
        when(certificateRepository.findOthersByLeader(leader.getId(), "CERT-1")).thenReturn(List.of(higher));
        service.revoke("CERT-1", reason("Sai thông tin"), admin);
        assertEquals(WorkflowApprovalStatus.REVOKED, certificate.getApprovalStatus());
    }

    @Test
    void equalLevelCertificatesDoNotBlockEachOther() {
        TrainingCourse sameCourse = course("K3", "HT_II_B");
        certificate.getCourse().getCourseType().setResultingLevel("HT_II");
        Certificate sameLevel = certificate("CERT-3", sameCourse, WorkflowApprovalStatus.APPROVED);
        register(sameLevel, participation(sameCourse, "HT_II_B"));
        when(certificateRepository.findOthersByLeader(leader.getId(), "CERT-1")).thenReturn(List.of(sameLevel));

        service.revoke("CERT-1", reason("Sai thông tin"), admin);

        assertEquals(WorkflowApprovalStatus.REVOKED, certificate.getApprovalStatus());
    }

    @Test
    void disabledCourseBlocksRevoke() {
        course.setStatus(Boolean.FALSE);

        ApiException error = assertThrows(ApiException.class, () -> service.revoke("CERT-1", reason("x"), admin));

        assertTrue(error.getMessage().contains("vô hiệu hóa"));
        assertEquals(WorkflowApprovalStatus.APPROVED, certificate.getApprovalStatus());
    }

    @Test
    void disabledCourseBlocksApproval() {
        certificate.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        course.setStatus(Boolean.FALSE);

        assertThrows(ApiException.class, () -> service.approve("CERT-1", new CertificateApproveRequest(), admin));
        assertEquals(WorkflowApprovalStatus.PENDING, certificate.getApprovalStatus());
    }

    // ---------------------------------------------------------------- cấp lại

    @Test
    void reissueRestoresApprovedStatusAndReevaluatesFinishedCourse() {
        service.revoke("CERT-1", reason("Sai thông tin"), admin);
        course.setFinishedAt(OffsetDateTime.now());

        CertificateResponse response = service.reissue("CERT-1", reissueReason("Đã bổ sung hồ sơ"), admin);

        assertEquals(WorkflowApprovalStatus.APPROVED, response.getApprovalStatus());
        assertEquals(WorkflowApprovalStatus.APPROVED, participation.getCertificateApprovalStatus());
        assertEquals("Đã bổ sung hồ sơ", certificate.getReissueReason());
        assertEquals("leader-1", certificate.getReissuedBy());
        assertNotNull(certificate.getReissuedAt());
        assertNull(certificate.getRevocationSource());
        // Khóa đã kết thúc: đánh giá lại để hồ sơ đạt và thăng cấp lại.
        verify(courseFinishService).reevaluateIfFinished(eq(participation), eq("leader-1"));
    }

    @Test
    void reissueOnUnfinishedCourseClearsTheFinalResult() {
        service.revoke("CERT-1", reason("Sai thông tin"), admin);

        service.reissue("CERT-1", reissueReason("Đã bổ sung hồ sơ"), admin);

        assertNull(participation.getFinalResult());
        verify(courseFinishService, never()).reevaluateIfFinished(any(), any());
    }

    @Test
    void reissueRequiresAReasonAndARevokedCertificate() {
        assertThrows(ApiException.class, () -> service.reissue("CERT-1", reissueReason("x"), admin));
        service.revoke("CERT-1", reason("Sai thông tin"), admin);
        assertThrows(ApiException.class, () -> service.reissue("CERT-1", reissueReason(" "), admin));
        assertEquals(WorkflowApprovalStatus.REVOKED, certificate.getApprovalStatus());
    }

    @Test
    void certificateRevokedByDisabledCourseCannotBeReissuedIndividually() {
        certificate.setApprovalStatus(WorkflowApprovalStatus.REVOKED);
        certificate.setRevocationSource(CertificateRevocationSource.COURSE_DISABLED);

        ApiException error = assertThrows(ApiException.class, () -> service.reissue("CERT-1", reissueReason("x"), admin));

        assertTrue(error.getMessage().contains("bật lại khóa"));
        assertEquals(WorkflowApprovalStatus.REVOKED, certificate.getApprovalStatus());
    }

    @Test
    void reissueMustStartFromTheLowestRevokedLevel() {
        // Cả hai chứng nhận đều bị thu hồi: cấp lại cấp II trước khi cấp I bị chặn.
        TrainingCourse higherCourse = course("K2", "HT_II");
        Certificate higher = certificate("CERT-2", higherCourse, WorkflowApprovalStatus.REVOKED);
        higher.setRevocationSource(CertificateRevocationSource.MANUAL);
        register(higher, participation(higherCourse, "HT_II"));
        certificate.setApprovalStatus(WorkflowApprovalStatus.REVOKED);
        certificate.setRevocationSource(CertificateRevocationSource.MANUAL);
        when(certificateRepository.findOthersByLeader(leader.getId(), "CERT-2")).thenReturn(List.of(certificate));
        when(certificateRepository.findOthersByLeader(leader.getId(), "CERT-1")).thenReturn(List.of(higher));

        ApiException error = assertThrows(ApiException.class, () -> service.reissue("CERT-2", reissueReason("x"), admin));
        assertTrue(error.getMessage().contains("cấp thấp nhất"));
        assertEquals(WorkflowApprovalStatus.REVOKED, higher.getApprovalStatus());

        service.reissue("CERT-1", reissueReason("x"), admin);
        assertEquals(WorkflowApprovalStatus.APPROVED, certificate.getApprovalStatus());
    }

    @Test
    void disabledCourseBlocksReissue() {
        service.revoke("CERT-1", reason("Sai thông tin"), admin);
        course.setStatus(Boolean.FALSE);

        assertThrows(ApiException.class, () -> service.reissue("CERT-1", reissueReason("x"), admin));
        assertEquals(WorkflowApprovalStatus.REVOKED, certificate.getApprovalStatus());
    }
}
