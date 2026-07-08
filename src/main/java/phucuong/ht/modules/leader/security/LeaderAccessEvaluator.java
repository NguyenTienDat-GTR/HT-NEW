package phucuong.ht.modules.leader.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.repository.LeaderRepository;

import java.util.UUID;

@Component("leaderAccessEvaluator")
@RequiredArgsConstructor
public class LeaderAccessEvaluator {

    public static final String CREATE_PARISH = "organization.leader.create.parish";
    public static final String READ_PARISH = "organization.leader.read.parish";
    public static final String UPDATE_PARISH = "organization.leader.update.parish";

    private final AccountRepository accountRepository;
    private final LeaderRepository leaderRepository;
    private final securityUtils securityUtils;

    public boolean canCreate(Authentication authentication) {
        return hasAuthority(authentication, CREATE_PARISH);
    }

    public boolean canList(Authentication authentication) {
        return hasAuthority(authentication, READ_PARISH);
    }

    public boolean canRead(Authentication authentication, UUID id) {
        return hasParishScopedAccess(authentication, id, READ_PARISH);
    }

    public boolean canUpdate(Authentication authentication, UUID id) {
        return hasParishScopedAccess(authentication, id, UPDATE_PARISH);
    }

    public boolean canToggle(Authentication authentication, UUID id) {
        return hasParishScopedAccess(authentication, id, UPDATE_PARISH);
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
}
