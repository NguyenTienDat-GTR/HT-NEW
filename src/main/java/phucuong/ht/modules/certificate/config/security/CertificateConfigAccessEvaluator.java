package phucuong.ht.modules.certificate.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;

// Cấu hình chứng nhận quản lý ở phạm vi giáo phận.
@Component("certificateConfigAccessEvaluator")
@RequiredArgsConstructor
public class CertificateConfigAccessEvaluator {

    private final securityUtils securityUtils;

    public boolean canRead(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "training.certificate_config.read.diocese");
    }

    public boolean canManage(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "training.certificate_config.update.diocese");
    }
}
