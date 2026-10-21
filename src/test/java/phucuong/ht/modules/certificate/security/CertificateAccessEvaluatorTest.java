package phucuong.ht.modules.certificate.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class CertificateAccessEvaluatorTest {

    @Test
    void superAdminCannotApproveEvenWhenAuthorityIsPresent() {
        CertificateAccessEvaluator evaluator = new CertificateAccessEvaluator(
                new securityUtils(), mock(phucuong.ht.modules.auth.repository.AccountRepository.class),
                mock(phucuong.ht.modules.certificate.repository.CertificateRepository.class),
                new phucuong.ht.modules.training.TrainingWorkflowSupport());
        var authentication = new UsernamePasswordAuthenticationToken(
                "super-admin", null,
                List.of(
                        () -> "ROLE_SUPER_ADMIN",
                        () -> "training.certificate.approve.diocese"));

        assertFalse(evaluator.canApprove(authentication, "certificate-1"));
    }

    @Test
    void superAdminCannotRevokeEvenWhenAuthorityIsPresent() {
        CertificateAccessEvaluator evaluator = new CertificateAccessEvaluator(
                new securityUtils(), mock(phucuong.ht.modules.auth.repository.AccountRepository.class),
                mock(phucuong.ht.modules.certificate.repository.CertificateRepository.class),
                new phucuong.ht.modules.training.TrainingWorkflowSupport());
        var authentication = new UsernamePasswordAuthenticationToken(
                "super-admin", null,
                List.of(
                        () -> "ROLE_SUPER_ADMIN",
                        () -> "training.certificate.revoke.diocese"));

        assertFalse(evaluator.canRevoke(authentication, "certificate-1"));
    }
}
