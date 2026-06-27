package phucuong.ht.modules.diocese.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;

@Component("dioceseAccessEvaluator")
@RequiredArgsConstructor
public class DioceseAccessEvaluator {

    public static final String CREATE_ALL = "organization.diocese.create.all";
    public static final String READ_ALL = "organization.diocese.read.all";
    public static final String UPDATE_ALL = "organization.diocese.update.all";
    public static final String DELETE_ALL = "organization.diocese.delete.all";
    public static final String READ_DIOCESE = "organization.diocese.read.diocese";
    public static final String UPDATE_DIOCESE = "organization.diocese.update.diocese";

    private final AccountRepository accountRepository;
    private final securityUtils securityUtils;

    public boolean canCreate(Authentication authentication) {
        return hasAuthority(authentication, CREATE_ALL);
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_ALL);
    }

    public boolean canRead(Authentication authentication, String dioceseCode) {
        return hasAuthority(authentication, READ_ALL)
                || hasScopedAccess(authentication, dioceseCode, READ_DIOCESE);
    }

    public boolean canUpdate(Authentication authentication, String dioceseCode) {
        return hasAuthority(authentication, UPDATE_ALL)
                || hasScopedAccess(authentication, dioceseCode, UPDATE_DIOCESE);
    }

    public boolean canDelete(Authentication authentication) {
        return hasAuthority(authentication, DELETE_ALL);
    }

    private boolean hasScopedAccess(Authentication authentication, String dioceseCode, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || dioceseCode == null || dioceseCode.isBlank()) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDioceseCodeByUsername(username)
                .map(dioceseCode::equals)
                .orElse(false);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authority == null || authority.isBlank()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
