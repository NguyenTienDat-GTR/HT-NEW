package phucuong.ht.modules.certificate.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

@Component("certificateAccessEvaluator")
@RequiredArgsConstructor
public class CertificateAccessEvaluator {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CertificateRepository certificateRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    public boolean canCreate(Authentication authentication, String courseCode) {
        return trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .map(course -> trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())
                        ? securityUtils.hasAuthority(authentication, "training.certificate.create.deanery")
                        : securityUtils.hasAuthority(authentication, "training.certificate.create.diocese"))
                .orElse(false);
    }

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "training.certificate.read.deanery")
                || securityUtils.hasAuthority(authentication, "training.certificate.read.diocese");
    }

    public boolean canRead(Authentication authentication, String certificateCode) {
        return hasScopedAccess(authentication, certificateCode, "read");
    }

    public boolean canUpdate(Authentication authentication, String certificateCode) {
        return hasScopedAccess(authentication, certificateCode, "update");
    }

    public boolean canDelete(Authentication authentication, String certificateCode) {
        return hasScopedAccess(authentication, certificateCode, "delete");
    }

    public boolean canApprove(Authentication authentication, String certificateCode) {
        return hasScopedAccess(authentication, certificateCode, "approve");
    }

    private boolean hasScopedAccess(Authentication authentication, String certificateCode, String action) {
        return certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .map(certificate -> {
                    String username = securityUtils.currentUsername(authentication);
                    if (username == null || username.isBlank() || certificate.getCourse() == null) {
                        return false;
                    }
                    if (trainingWorkflowSupport.isDeaneryManaged(certificate.getCourse().getCourseType())) {
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
