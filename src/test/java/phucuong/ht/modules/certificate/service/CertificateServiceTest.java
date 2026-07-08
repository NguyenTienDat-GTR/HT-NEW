package phucuong.ht.modules.certificate.service;

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
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.CourseType;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        certificateService = new CertificateService(
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

        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setLeaderLevel(LeaderLevel.HT_XU);

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("SMKNI");
        course.setCourseType(CourseType.SMKN_I);

        Certificate certificate = new Certificate();
        certificate.setCertificateCode("CERT-1");
        certificate.setLeader(leader);
        certificate.setCourse(course);

        CourseParticipation participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setTotalScore(BigDecimal.valueOf(7));
        participation.setResult(CourseResult.COMPLETED);

        when(certificateRepository.findByCodeWithLeaderAndCourse("CERT-1")).thenReturn(Optional.of(certificate));
        when(courseParticipationRepository.findByLeaderIdAndCourseCode(leader.getId(), "SMKNI")).thenReturn(Optional.of(participation));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin-diocese", "leader-1", List.of("ADMIN_DIOCESE"), List.of("training.certificate.approve.diocese"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        var response = certificateService.approve("CERT-1", authentication);

        assertEquals(WorkflowApprovalStatus.APPROVED, response.getApprovalStatus());
        assertEquals(LeaderLevel.HT_I, leader.getLeaderLevel());
        verify(rankHistoryRepository).save(any());
        verify(domainEventPublisher).publishAfterCommit(any());
    }
}
