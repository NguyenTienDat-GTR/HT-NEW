package phucuong.ht.modules.certificate.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;

// Quyền thư viện cấu hình chứng nhận: training.certificate_config.{read,create,update,delete}.{diocese,deanery}.
// Evaluator chỉ kiểm tra người dùng có quyền ở một cấp; phạm vi dữ liệu (giáo phận / giáo hạt của chính mình)
// do CertificateConfigScopeResolver và service kiểm tra.
@Component("certificateConfigAccessEvaluator")
@RequiredArgsConstructor
public class CertificateConfigAccessEvaluator {

    private final securityUtils securityUtils;

    public boolean canRead(Authentication authentication) {
        return hasAction(authentication, "read");
    }

    public boolean canCreate(Authentication authentication) {
        return hasAction(authentication, "create");
    }

    public boolean canUpdate(Authentication authentication) {
        return hasAction(authentication, "update");
    }

    public boolean canDelete(Authentication authentication) {
        return hasAction(authentication, "delete");
    }

    private boolean hasAction(Authentication authentication, String action) {
        return securityUtils.hasAuthority(authentication, "training.certificate_config." + action + ".diocese")
                || securityUtils.hasAuthority(authentication, "training.certificate_config." + action + ".deanery");
    }
}
