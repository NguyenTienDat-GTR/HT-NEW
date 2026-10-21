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
                mock(phucuong.ht.modules.leader.repository.LeaderRepository.class),
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
                mock(phucuong.ht.modules.leader.repository.LeaderRepository.class),
                new phucuong.ht.modules.training.TrainingWorkflowSupport());
        var authentication = new UsernamePasswordAuthenticationToken(
                "super-admin", null,
                List.of(
                        () -> "ROLE_SUPER_ADMIN",
                        () -> "training.certificate.revoke.diocese"));

        assertFalse(evaluator.canRevoke(authentication, "certificate-1"));
    }

    @Test
    void deaneryAdminReadsCertificatesOfLeaderInOwnDeaneryRegardlessOfCourseLevel() {
        var accountRepository = mock(phucuong.ht.modules.auth.repository.AccountRepository.class);
        var leaderRepository = mock(phucuong.ht.modules.leader.repository.LeaderRepository.class);
        java.util.UUID deaneryId = java.util.UUID.randomUUID();
        java.util.UUID otherDeaneryId = java.util.UUID.randomUUID();
        java.util.UUID leaderId = java.util.UUID.randomUUID();
        java.util.UUID outsideLeaderId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(java.util.Optional.of(deaneryId));
        org.mockito.Mockito.when(leaderRepository.findDeaneryIdByLeaderId(leaderId)).thenReturn(java.util.Optional.of(deaneryId));
        org.mockito.Mockito.when(leaderRepository.findDeaneryIdByLeaderId(outsideLeaderId)).thenReturn(java.util.Optional.of(otherDeaneryId));
        CertificateAccessEvaluator evaluator = new CertificateAccessEvaluator(
                new securityUtils(), accountRepository,
                mock(phucuong.ht.modules.certificate.repository.CertificateRepository.class), leaderRepository,
                new phucuong.ht.modules.training.TrainingWorkflowSupport());
        var principal = phucuong.ht.modules.auth.model.AuthUserDetails.tokenPrincipal(
                "admin-deanery", null, List.of("ADMIN_DEANERY"), List.of(CertificateAccessEvaluator.LEADER_CERTIFICATE_READ_DEANERY));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        org.junit.jupiter.api.Assertions.assertTrue(evaluator.canReadLeaderCertificates(authentication, leaderId));
        assertFalse(evaluator.canReadLeaderCertificates(authentication, outsideLeaderId));
        // Không mở menu Chứng nhận: danh sách chứng nhận vẫn cần quyền training.certificate.read.
        assertFalse(evaluator.canList(authentication));
    }
}
