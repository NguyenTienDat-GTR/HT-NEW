package phucuong.ht.modules.training.registrationTemplate.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;

/** Quyền mẫu danh sách đăng ký: training.registration_template.{read,update}.{diocese,deanery}; super admin luôn được. */
@Component("registrationTemplateAccessEvaluator")
@RequiredArgsConstructor
public class RegistrationTemplateAccessEvaluator {

    public static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    private final securityUtils securityUtils;

    public boolean canRead(Authentication authentication) {
        return isSuperAdmin(authentication) || has(authentication, "read", "diocese") || has(authentication, "update", "diocese")
                || has(authentication, "read", "deanery") || has(authentication, "update", "deanery");
    }

    public boolean canUpdate(Authentication authentication) {
        return isSuperAdmin(authentication) || has(authentication, "update", "diocese") || has(authentication, "update", "deanery");
    }

    public boolean has(Authentication authentication, String action, String scope) {
        return securityUtils.hasAuthority(authentication, "training.registration_template." + action + "." + scope);
    }

    public boolean isSuperAdmin(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE);
    }
}
