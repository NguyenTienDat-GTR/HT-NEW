package phucuong.ht.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.auth.model.AuthUserDetails;

@Component("securityUtils")
public class securityUtils {

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";
    private static final String SUPER_ADMIN_AUDIT_ACTOR = "super-admin";

    public boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authority == null || authority.isBlank()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public String currentUsername(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthUserDetails principal) {
            return principal.getUsername();
        }
        return authentication.getName();
    }

    public String currentLeaderId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            return null;
        }
        return principal.getLeaderId();
    }

    public String currentAuditActor(Authentication authentication) {
        if (hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return SUPER_ADMIN_AUDIT_ACTOR;
        }

        String leaderId = currentLeaderId(authentication);
        if (StringUtils.hasText(leaderId)) {
            return leaderId;
        }

        String username = currentUsername(authentication);
        return StringUtils.hasText(username) ? username : null;
    }
}
