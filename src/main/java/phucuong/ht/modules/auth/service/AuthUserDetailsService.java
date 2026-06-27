package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.AuthPermissionQueryRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AuthPermissionQueryRepository authPermissionQueryRepository;

    @Override
    public AuthUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + username));

        List<String> roles = accountRoleRepository.findActiveRoleCodesByUsername(username);
        List<String> permissions = resolveAllowedPermissions(username);

        return AuthUserDetails.databasePrincipal(
                account.getUsername(),
                account.getPassword(),
                account.getLeader() != null ? account.getLeader().getLeaderCode() : null,
                roles,
                permissions,
                Boolean.TRUE.equals(account.getStatus())
        );
    }

    private List<String> resolveAllowedPermissions(String username) {
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();

        for (AuthPermissionQueryRepository.EffectivePermissionRow row : authPermissionQueryRepository.findEffectivePermissionsByUsername(username)) {
            if ("DENY".equalsIgnoreCase(row.effect())) {
                denied.add(row.permissionCode());
                allowed.remove(row.permissionCode());
                continue;
            }
            if (!denied.contains(row.permissionCode())) {
                allowed.add(row.permissionCode());
            }
        }

        return List.copyOf(allowed);
    }
}
