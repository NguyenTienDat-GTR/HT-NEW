package phucuong.ht.modules.deanery.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;

import java.util.UUID;

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
    public static final String TOGGLE_DIOCESE = "organization.deanery.toggle.diocese";
    public static final String READ_DEANERY = "organization.deanery.read.deanery";
    public static final String UPDATE_DEANERY = "organization.deanery.update.deanery";

    private final AccountRepository accountRepository;
    private final DeaneryRepository deaneryRepository;
    private final securityUtils securityUtils;

    public boolean canCreate(Authentication authentication) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, CREATE_ALL)
                || hasAuthority(authentication, CREATE_DIOCESE);
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_ALL)
                || hasAuthority(authentication, READ_DIOCESE)
                || hasAuthority(authentication, READ_DEANERY);
    }

    public boolean canRead(Authentication authentication, UUID deaneryId) {
        return hasAuthority(authentication, READ_ALL)
                || hasDioceseScopedAccess(authentication, deaneryId, READ_DIOCESE)
                || hasDeaneryScopedAccess(authentication, deaneryId, READ_DEANERY);
    }

    public boolean canUpdate(Authentication authentication, UUID deaneryId) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, UPDATE_ALL)
                || hasDeaneryScopedAccess(authentication, deaneryId, UPDATE_DEANERY);
    }

    public boolean canDelete(Authentication authentication) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, DELETE_ALL);
    }

    public boolean canToggle(Authentication authentication, UUID deaneryId) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, UPDATE_ALL)
                || hasDioceseScopedAccess(authentication, deaneryId, TOGGLE_DIOCESE);
    }

    private boolean hasDioceseScopedAccess(Authentication authentication, UUID deaneryId, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || deaneryId == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeDioceseId -> deaneryRepository.findDioceseIdByDeaneryId(deaneryId)
                        .map(activeDioceseId::equals))
                .orElse(false);
    }

    private boolean hasDeaneryScopedAccess(Authentication authentication, UUID deaneryId, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || deaneryId == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDeaneryIdByUsername(username)
                .map(deaneryId::equals)
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

    private boolean hasRole(Authentication authentication, String roleCode) {
        return hasAuthority(authentication, "ROLE_" + roleCode);
    }
}
