package phucuong.ht.modules.badge.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;

// Quyền thư viện cấu hình thẻ tham dự: training.badge_config.{read,create,update,delete}.{diocese,deanery}.
// Phạm vi dữ liệu (giáo phận / giáo hạt của chính mình) do CertificateConfigScopeResolver và service kiểm tra.
@Component("badgeConfigAccessEvaluator")
@RequiredArgsConstructor
public class BadgeConfigAccessEvaluator {

    public static final String PREFIX = "training.badge_config.";

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
        return securityUtils.hasAuthority(authentication, PREFIX + action + ".diocese")
                || securityUtils.hasAuthority(authentication, PREFIX + action + ".deanery");
    }
}
