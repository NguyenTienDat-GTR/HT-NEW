package phucuong.ht.modules.parish.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.UUID;

@Component("parishAccessEvaluator")
@RequiredArgsConstructor
public class ParishAccessEvaluator {

    public static final String CREATE_ALL = "organization.parish.create.all";
    public static final String READ_ALL = "organization.parish.read.all";
    public static final String UPDATE_ALL = "organization.parish.update.all";
    public static final String TOGGLE_ALL = "organization.parish.toggle.all";
    public static final String CREATE_DIOCESE = "organization.parish.create.diocese";
    public static final String READ_DIOCESE = "organization.parish.read.diocese";
    public static final String UPDATE_DIOCESE = "organization.parish.update.diocese";
    public static final String TOGGLE_DIOCESE = "organization.parish.toggle.diocese";
    public static final String CREATE_DEANERY = "organization.parish.create.deanery";
    public static final String READ_DEANERY = "organization.parish.read.deanery";
    public static final String UPDATE_DEANERY = "organization.parish.update.deanery";
    public static final String TOGGLE_DEANERY = "organization.parish.toggle.deanery";
    public static final String READ_PARISH = "organization.parish.read.parish";
    public static final String UPDATE_PARISH = "organization.parish.update.parish";

    private final AccountRepository accountRepository;
    private final ParishRepository parishRepository;
    private final securityUtils securityUtils;

    public boolean canCreate(Authentication authentication) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, CREATE_ALL)
                || hasAuthority(authentication, CREATE_DIOCESE)
                || hasAuthority(authentication, CREATE_DEANERY);
    }

    // Đổi giáo xứ cha của giáo họ / giáo điểm: người có quyền tạo giáo xứ trong đúng phạm vi giáo hạt / giáo phận của đơn vị đó.
    public boolean canChangeParent(Authentication authentication, UUID parishId) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, CREATE_ALL)
                || hasDioceseScopedAccess(authentication, parishId, CREATE_DIOCESE)
                || hasDeaneryScopedAccess(authentication, parishId, CREATE_DEANERY);
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_ALL)
                || hasAuthority(authentication, READ_DIOCESE)
                || hasAuthority(authentication, READ_DEANERY)
                || hasAuthority(authentication, READ_PARISH);
    }

    public boolean canRead(Authentication authentication, UUID parishId) {
        return hasAuthority(authentication, READ_ALL)
                || hasDioceseScopedAccess(authentication, parishId, READ_DIOCESE)
                || hasDeaneryScopedAccess(authentication, parishId, READ_DEANERY)
                || hasParishScopedAccess(authentication, parishId, READ_PARISH);
    }

    public boolean canUpdate(Authentication authentication, UUID parishId) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        return hasAuthority(authentication, UPDATE_ALL)
                || hasParishScopedAccess(authentication, parishId, UPDATE_PARISH);
    }

    // Admin giáo hạt bật/tắt giáo xứ trong giáo hạt; admin giáo phận bật/tắt giáo xứ trong giáo phận (quyền toggle.diocese).
    public boolean canToggle(Authentication authentication, UUID parishId) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            return false;
        }
        if (hasRole(authentication, "ADMIN_DEANERY") && hasDeaneryScopedAccess(authentication, parishId, TOGGLE_DEANERY)) {
            return true;
        }
        return hasRole(authentication, "ADMIN_DIOCESE") && hasDioceseScopedAccess(authentication, parishId, TOGGLE_DIOCESE);
    }

    private boolean hasDioceseScopedAccess(Authentication authentication, UUID parishId, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || parishId == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeDioceseId -> parishRepository.findDioceseIdByParishId(parishId)
                        .map(activeDioceseId::equals))
                .orElse(false);
    }

    private boolean hasDeaneryScopedAccess(Authentication authentication, UUID parishId, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || parishId == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDeaneryIdByUsername(username)
                .flatMap(activeDeaneryId -> parishRepository.findDeaneryIdByParishId(parishId)
                        .map(activeDeaneryId::equals))
                .orElse(false);
    }

    private boolean hasParishScopedAccess(Authentication authentication, UUID parishId, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || parishId == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveParishIdByUsername(username)
                .map(parishId::equals)
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
