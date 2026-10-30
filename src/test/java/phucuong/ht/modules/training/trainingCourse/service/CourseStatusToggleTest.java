package phucuong.ht.modules.training.trainingCourse.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.certificate.service.CertificateLifecycleService;
import phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.CourseStatusToggleRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.CourseStatusImpactResponse;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseStatusToggleTest {

    @Mock
    private TrainingCourseRepository trainingCourseRepository;
    @Mock
    private CourseParticipationRepository courseParticipationRepository;
    @Mock
    private CertificateLifecycleService certificateLifecycleService;
    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private CertificateRealtimeNotifier certificateRealtimeNotifier;
    @Mock
    private CourseActivityGuard courseActivityGuard;
    @Mock
    private phucuong.ht.modules.training.courseScoreComponent.service.CourseScoreComponentService courseScoreComponentService;
    @Spy
    private CommonValidationHelper commonValidationHelper = new CommonValidationHelper(new securityUtils());
    @Spy
    private phucuong.ht.modules.training.TrainingWorkflowSupport trainingWorkflowSupport = new phucuong.ht.modules.training.TrainingWorkflowSupport();

    @org.mockito.Mock
    private phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

    @InjectMocks
    private TrainingCourseService service;

    private TrainingCourse course;
    private final Authentication admin = adminAuthentication();

    private static Authentication adminAuthentication() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin", "leader-1", List.of("ADMIN_DIOCESE"), List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @BeforeEach
    void setUp() {
        course = new TrainingCourse();
        course.setCourseCode("K1");
        course.setStatus(Boolean.TRUE);
        course.setRecipientUsernames(new java.util.LinkedHashSet<>());
        org.mockito.Mockito.lenient().when(trainingCourseRepository.findByCodeWithType("K1")).thenReturn(Optional.of(course));
    }

    private CourseStatusToggleRequest request(String confirmation, Boolean target) {
        CourseStatusToggleRequest request = new CourseStatusToggleRequest();
        request.setConfirmationText(confirmation);
        request.setTargetStatus(target);
        return request;
    }

    @Test
    void confirmationTextAndTargetStatusAreVerifiedByTheServer() {
        assertThrows(ApiException.class, () -> service.toggleStatus("K1", request("ok", false), admin));
        assertThrows(ApiException.class, () -> service.toggleStatus("K1", request("Xác nhận", null), admin));
        assertTrue(course.getStatus());
        verify(certificateLifecycleService, never()).revoke(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void togglingToTheCurrentStatusIsRejected() {
        // Dữ liệu đã đổi (đã bật) trong lúc người dùng đang xem hộp thoại: không đổi nhầm.
        ApiException error = assertThrows(ApiException.class, () -> service.toggleStatus("K1", request("Xác nhận", true), admin));

        assertTrue(error.getMessage().contains("tải lại"));
    }

    @Test
    void disablingRevokesEveryApprovedCertificateIgnoringLevelOrder() {
        Certificate first = new Certificate();
        first.setCertificateCode("C1");
        Certificate second = new Certificate();
        second.setCertificateCode("C2");
        when(certificateLifecycleService.approvedOf("K1")).thenReturn(List.of(first, second));

        TrainingCourseResponse response = service.toggleStatus("K1", request("Xác nhận", false), admin);

        assertFalse(course.getStatus());
        assertFalse(response.getStatus());
        verify(certificateLifecycleService).revoke(eq(first), eq(CertificateLifecycleService.COURSE_DISABLED_REASON),
                eq(CertificateRevocationSource.COURSE_DISABLED), eq(false), eq("leader-1"), any());
        verify(certificateLifecycleService).revoke(eq(second), eq(CertificateLifecycleService.COURSE_DISABLED_REASON),
                eq(CertificateRevocationSource.COURSE_DISABLED), eq(false), eq("leader-1"), any());
        verify(certificateLifecycleService, never()).restore(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
    }

    @Test
    void enablingRestoresOnlyCertificatesRevokedByTheDisable() {
        course.setStatus(Boolean.FALSE);
        Certificate byDisable = new Certificate();
        byDisable.setCertificateCode("C1");
        when(certificateLifecycleService.revokedByCourseDisableOf("K1")).thenReturn(List.of(byDisable));

        service.toggleStatus("K1", request("Xác nhận", true), admin);

        assertTrue(course.getStatus());
        verify(certificateLifecycleService).restore(eq(byDisable), eq(null), eq(false), eq("leader-1"), any());
        // Không đụng tới chứng nhận bị thu hồi vì lý do khác: truy vấn chỉ lấy nguồn COURSE_DISABLED.
        verify(certificateLifecycleService, never()).approvedOf(any());
    }

    @Test
    void statusImpactCountsCertificatesAffected() {
        when(certificateRepository.countByCourseAndStatus("K1", WorkflowApprovalStatus.APPROVED)).thenReturn(3L);
        when(certificateRepository.countRevokedByCourseAndSource("K1", CertificateRevocationSource.COURSE_DISABLED)).thenReturn(2L);

        CourseStatusImpactResponse impact = service.statusImpact("K1");

        assertEquals(3, impact.getApprovedCertificates());
        assertEquals(2, impact.getRestorableCertificates());
        assertTrue(impact.isActive());
    }
}
