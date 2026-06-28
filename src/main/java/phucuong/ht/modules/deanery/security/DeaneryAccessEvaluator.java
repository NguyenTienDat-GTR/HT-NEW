package phucuong.ht.modules.deanery.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;

@Component("deaneryAccessEvaluator")
@RequiredArgsConstructor
public class DeaneryAccessEvaluator {

    public static final String CREATE_ALL = "organization.deanery.create.all";
    public static final String READ_ALL = "organization.deanery.read.all";
    public static final String UPDATE_ALL = "organization.deanery.update.all";
    public static final String DELETE_ALL = "organization.deanery.delete.all";
    public static final String CREATE_DIOCESE = "organization.deanery.create.diocese";
    public static final String READ_DIOCESE = "organization.deanery.read.diocese";
    public static final String UPDATE_DIOCESE = "organization.deanery.update.diocese";
    public static final String DELETE_DIOCESE = "organization.deanery.delete.diocese";
    public static final String READ_DEANERY = "organization.deanery.read.deanery";
    public static final String UPDATE_DEANERY = "organization.deanery.update.deanery";

    private final AccountRepository accountRepository;
    private final DeaneryRepository deaneryRepository;
    private final securityUtils securityUtils;

    public boolean canCreate(Authentication authentication) {
        return hasAuthority(authentication, CREATE_ALL)
                || hasAuthority(authentication, CREATE_DIOCESE);
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_ALL)
                || hasAuthority(authentication, READ_DIOCESE)
                || hasAuthority(authentication, READ_DEANERY);
    }

    public boolean canRead(Authentication authentication, String deaneryCode) {
        return hasAuthority(authentication, READ_ALL)
                || hasDioceseScopedAccess(authentication, deaneryCode, READ_DIOCESE)
                || hasDeaneryScopedAccess(authentication, deaneryCode, READ_DEANERY);
    }

    public boolean canUpdate(Authentication authentication, String deaneryCode) {
        return hasAuthority(authentication, UPDATE_ALL)
                || hasDioceseScopedAccess(authentication, deaneryCode, UPDATE_DIOCESE)
                || hasDeaneryScopedAccess(authentication, deaneryCode, UPDATE_DEANERY);
    }

    public boolean canDelete(Authentication authentication) {
        return hasAuthority(authentication, DELETE_ALL)
                || hasAuthority(authentication, DELETE_DIOCESE);
    }

    private boolean hasDioceseScopedAccess(Authentication authentication, String deaneryCode, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || deaneryCode == null || deaneryCode.isBlank()) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDioceseCodeByUsername(username)
                .flatMap(activeDioceseCode -> deaneryRepository.findDioceseCodeByDeaneryCode(deaneryCode)
                        .map(activeDioceseCode::equals))
                .orElse(false);
    }

    private boolean hasDeaneryScopedAccess(Authentication authentication, String deaneryCode, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || deaneryCode == null || deaneryCode.isBlank()) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDeaneryCodeByUsername(username)
                .map(deaneryCode::equals)
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
