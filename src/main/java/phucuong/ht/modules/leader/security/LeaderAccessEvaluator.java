package phucuong.ht.modules.leader.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.repository.LeaderRepository;

import java.util.UUID;

@Component("leaderAccessEvaluator")
@RequiredArgsConstructor
public class LeaderAccessEvaluator {

    public static final String CREATE_DIOCESE = "organization.leader.create.diocese";
    public static final String CREATE_DEANERY = "organization.leader.create.deanery";
    public static final String CREATE_PARISH = "organization.leader.create.parish";
    public static final String READ_DIOCESE = "organization.leader.read.diocese";
    public static final String READ_DEANERY = "organization.leader.read.deanery";
    public static final String READ_PARISH = "organization.leader.read.parish";
    public static final String UPDATE_DIOCESE = "organization.leader.update.diocese";
    public static final String UPDATE_DEANERY = "organization.leader.update.deanery";
    public static final String UPDATE_PARISH = "organization.leader.update.parish";
    public static final String TRANSFER_DIOCESE = "organization.leader.transfer.diocese";
    public static final String TRANSFER_DEANERY = "organization.leader.transfer.deanery";
    public static final String TOGGLE_PARISH = "organization.leader.toggle.parish";

    private final AccountRepository accountRepository;
    private final LeaderRepository leaderRepository;
    private final securityUtils securityUtils;

    public boolean canCreate(Authentication authentication) {
        return (hasRole(authentication, "ADMIN_DIOCESE") && hasAuthority(authentication, CREATE_DIOCESE))
                || (hasRole(authentication, "ADMIN_DEANERY") && hasAuthority(authentication, CREATE_DEANERY))
                || (hasRole(authentication, "ADMIN_PARISH") && hasAuthority(authentication, CREATE_PARISH));
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_DIOCESE)
                || hasAuthority(authentication, READ_DEANERY)
                || hasAuthority(authentication, READ_PARISH);
    }

    public boolean canRead(Authentication authentication, UUID id) {
        return hasDioceseScopedAccess(authentication, id, READ_DIOCESE)
                || hasDeaneryScopedAccess(authentication, id, READ_DEANERY)
                || hasParishScopedAccess(authentication, id, READ_PARISH);
    }

    public boolean canUpdate(Authentication authentication, UUID id) {
        return (hasRole(authentication, "ADMIN_DIOCESE") && hasDioceseScopedAccess(authentication, id, UPDATE_DIOCESE))
                || (hasRole(authentication, "ADMIN_DEANERY") && hasDeaneryScopedAccess(authentication, id, UPDATE_DEANERY))
                || (hasRole(authentication, "ADMIN_PARISH") && hasParishScopedAccess(authentication, id, UPDATE_PARISH));
    }

    // Quyền chuyển giáo xứ tách riêng khỏi quyền sửa; quản trị giáo xứ không có quyền này.
    public boolean canTransfer(Authentication authentication, UUID id) {
        return (hasRole(authentication, "ADMIN_DIOCESE") && hasDioceseScopedAccess(authentication, id, TRANSFER_DIOCESE))
                || (hasRole(authentication, "ADMIN_DEANERY") && hasDeaneryScopedAccess(authentication, id, TRANSFER_DEANERY));
    }

    public boolean canToggle(Authentication authentication, UUID id) {
        return hasRole(authentication, "ADMIN_PARISH") && hasParishScopedAccess(authentication, id, TOGGLE_PARISH);
    }

    private boolean hasDioceseScopedAccess(Authentication authentication, UUID id, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || id == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeDioceseId -> leaderRepository.findDioceseIdByLeaderId(id)
                        .map(activeDioceseId::equals))
                .orElse(false);
    }

    private boolean hasDeaneryScopedAccess(Authentication authentication, UUID id, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || id == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveDeaneryIdByUsername(username)
                .flatMap(activeDeaneryId -> leaderRepository.findDeaneryIdByLeaderId(id)
                        .map(activeDeaneryId::equals))
                .orElse(false);
    }

    private boolean hasParishScopedAccess(Authentication authentication, UUID id, String scopedAuthority) {
        if (!hasAuthority(authentication, scopedAuthority) || id == null) {
            return false;
        }

        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }

        return accountRepository.findActiveParishIdByUsername(username)
                .flatMap(activeParishId -> leaderRepository.findParishIdByLeaderId(id)
                        .map(activeParishId::equals))
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
        return authentication != null
                && authentication.getPrincipal() instanceof AuthUserDetails principal
                && principal.getRoles().contains(roleCode);
    }
}
