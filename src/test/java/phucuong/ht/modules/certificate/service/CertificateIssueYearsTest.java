package phucuong.ht.modules.certificate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.security.AccountScopeResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.certificate.security.CertificateAccessEvaluator;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.service.CourseFinishService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateIssueYearsTest {

    private CertificateRepository certificateRepository;
    private AccountScopeValidationHelper scopeHelper;
    private AccountRepository accountRepository;
    private CertificateService service;

    @BeforeEach
    void setUp() {
        certificateRepository = mock(CertificateRepository.class);
        scopeHelper = mock(AccountScopeValidationHelper.class);
        accountRepository = mock(AccountRepository.class);
        CommonValidationHelper validationHelper = new CommonValidationHelper(new securityUtils());
        service = new CertificateService(
                certificateRepository, mock(TrainingCourseRepository.class), mock(LeaderRepository.class),
                mock(CourseParticipationRepository.class), mock(CertificateLifecycleService.class), new CourseActivityGuard(validationHelper),
                new TrainingWorkflowSupport(), scopeHelper, new AccountScopeResolver(new securityUtils(), accountRepository), validationHelper, mock(DomainEventPublisher.class),
                mock(CertificateRealtimeNotifier.class), mock(CertificateAccessEvaluator.class), mock(CourseFinishService.class), org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
    }

    private Authentication authentication(String authority) {
        return new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> authority));
    }

    @Test
    void returnsEveryIssueYearThatExistsNewestFirstWithoutDuplicates() {
        UUID diocese = UUID.randomUUID();
        when(accountRepository.findActiveDioceseIdByUsername("admin")).thenReturn(java.util.Optional.of(diocese));
        // Gồm cả năm rất cũ: không bị cắt theo số năm gần đây.
        when(certificateRepository.findDistinctIssueDates(diocese, null)).thenReturn(List.of(
                LocalDate.of(2024, 5, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2024, 12, 31), LocalDate.of(2009, 3, 3)));

        List<Integer> years = service.issueYears(authentication("training.certificate.read.diocese"));

        assertEquals(List.of(2026, 2024, 2009), years);
    }

    @Test
    void deaneryAccountsAreScopedToTheirDeanery() {
        UUID deanery = UUID.randomUUID();
        when(accountRepository.findActiveDeaneryIdByUsername("admin")).thenReturn(java.util.Optional.of(deanery));
        when(certificateRepository.findDistinctIssueDates(isNull(), org.mockito.ArgumentMatchers.eq(deanery))).thenReturn(List.of(LocalDate.of(2025, 7, 7)));

        assertEquals(List.of(2025), service.issueYears(authentication("training.certificate.read.deanery")));
    }

    @Test
    void accountsWithDioceseAndDeaneryPermissionsSeeTheUnionOfBothScopes() {
        UUID diocese = UUID.randomUUID();
        UUID deanery = UUID.randomUUID();
        when(accountRepository.findActiveDioceseIdByUsername("admin")).thenReturn(java.util.Optional.of(diocese));
        when(accountRepository.findActiveDeaneryIdByUsername("admin")).thenReturn(java.util.Optional.of(deanery));
        when(certificateRepository.findDistinctIssueDates(diocese, deanery)).thenReturn(List.of(LocalDate.of(2024, 5, 1), LocalDate.of(2025, 2, 2)));
        Authentication both = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(() -> "training.certificate.read.diocese", () -> "training.certificate.read.deanery"));

        assertEquals(List.of(2025, 2024), service.issueYears(both));
    }

    @Test
    void accountsWithoutReadPermissionAreRejected() {
        assertThrows(ApiException.class, () -> service.issueYears(authentication("training.certificate.approve.diocese")));
    }
}
