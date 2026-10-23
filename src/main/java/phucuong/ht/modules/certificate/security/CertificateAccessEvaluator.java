package phucuong.ht.modules.certificate.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;

@Component("certificateAccessEvaluator")
@RequiredArgsConstructor
public class CertificateAccessEvaluator {

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";
    public static final String LEADER_CERTIFICATE_READ_DEANERY = "organization.leader_certificate.read.deanery";
    public static final String LEADER_CERTIFICATE_READ_PARISH = "organization.leader_certificate.read.parish";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CertificateRepository certificateRepository;
    private final phucuong.ht.modules.leader.repository.LeaderRepository leaderRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "training.certificate.read.deanery")
                || securityUtils.hasAuthority(authentication, "training.certificate.read.diocese");
    }

    public boolean canRead(Authentication authentication, String certificateCode) {
        return hasScopedAccess(authentication, certificateCode, "read") || hasLeaderProfileAccess(authentication, certificateCode);
    }

    // Xem chứng nhận trong hồ sơ huynh trưởng: quyền organization.leader_certificate.read.{deanery,parish}
    // theo phạm vi quản lý huynh trưởng, không phụ thuộc cấp của khóa cấp chứng nhận và không mở menu Chứng nhận.
    public boolean canReadLeaderCertificates(Authentication authentication, java.util.UUID leaderId) {
        if (leaderId == null || securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        if (securityUtils.hasAuthority(authentication, LEADER_CERTIFICATE_READ_DEANERY)
                && accountRepository.findActiveDeaneryIdByUsername(username)
                .flatMap(activeId -> leaderRepository.findDeaneryIdByLeaderId(leaderId).map(activeId::equals))
                .orElse(false)) {
            return true;
        }
        return securityUtils.hasAuthority(authentication, LEADER_CERTIFICATE_READ_PARISH)
                && accountRepository.findActiveParishIdByUsername(username)
                .flatMap(activeId -> leaderRepository.findParishIdByLeaderId(leaderId).map(activeId::equals))
                .orElse(false);
    }

    private boolean hasLeaderProfileAccess(Authentication authentication, String certificateCode) {
        return certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .map(certificate -> certificate.getLeader() != null && canReadLeaderCertificates(authentication, certificate.getLeader().getId()))
                .orElse(false);
    }

    // Thu hồi chứng nhận đã duyệt: quyền training.certificate.revoke.{diocese,deanery} trong phạm vi quản lý.
    public boolean canRevoke(Authentication authentication, String certificateCode) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        return hasScopedAccess(authentication, certificateCode, "revoke");
    }

    // Cấp lại chứng nhận đã thu hồi: quyền training.certificate.reissue.{diocese,deanery} trong phạm vi quản lý.
    public boolean canReissue(Authentication authentication, String certificateCode) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        return hasScopedAccess(authentication, certificateCode, "reissue");
    }

    public boolean canApprove(Authentication authentication, String certificateCode) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        return hasScopedAccess(authentication, certificateCode, "approve");
    }

    // Duyệt hàng loạt: cần quyền duyệt chứng nhận ở một cấp; service kiểm tra phạm vi từng chứng nhận.
    public boolean canBulkApprove(Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        return securityUtils.hasAuthority(authentication, "training.certificate.approve.deanery")
                || securityUtils.hasAuthority(authentication, "training.certificate.approve.diocese");
    }

    private boolean hasScopedAccess(Authentication authentication, String certificateCode, String action) {
        return certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .map(certificate -> {
                    String username = securityUtils.currentUsername(authentication);
                    if (username == null || username.isBlank() || certificate.getCourse() == null) {
                        return false;
                    }
                    if (trainingWorkflowSupport.isDeaneryManaged(certificate.getCourse())) {
                        return securityUtils.hasAuthority(authentication, "training.certificate." + action + ".deanery")
                                && accountRepository.findActiveDeaneryIdByUsername(username)
                                .flatMap(activeId -> certificateRepository.findDeaneryIdByCertificateCode(certificateCode).map(activeId::equals))
                                .orElse(false);
                    }
                    return securityUtils.hasAuthority(authentication, "training.certificate." + action + ".diocese")
                            && accountRepository.findActiveDioceseIdByUsername(username)
                            .flatMap(activeId -> certificateRepository.findDioceseIdByCertificateCode(certificateCode).map(activeId::equals))
                            .orElse(false);
                })
                .orElse(false);
    }
}
