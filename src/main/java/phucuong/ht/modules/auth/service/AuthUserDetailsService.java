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

    private static final String EFFECT_ALLOW = "ALLOW";
    private static final String EFFECT_DENY = "DENY";
    private static final String SOURCE_ACCOUNT_OVERRIDE = "ACCOUNT_OVERRIDE";
    private static final String SOURCE_ROLE = "ROLE";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AuthPermissionQueryRepository authPermissionQueryRepository;

    @Override
    public AuthUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + username));

        List<String> roles = accountRoleRepository.findActiveRoleCodesByUsername(username);
        List<String> permissions = resolveAllowedPermissions(username);

        return AuthUserDetails.databasePrincipal(
                account.getUsername(),
                account.getPassword(),
                account.getLeader() != null && account.getLeader().getId() != null ? account.getLeader().getId().toString() : null,
                roles,
                permissions,
                Boolean.TRUE.equals(account.getStatus()),
                Boolean.TRUE.equals(account.getMustChangePassword())
        );
    }

    private List<String> resolveAllowedPermissions(String username) {
        Set<String> permissionCodes = new LinkedHashSet<>();
        Set<String> accountDenied = new LinkedHashSet<>();
        Set<String> accountAllowed = new LinkedHashSet<>();
        Set<String> roleAllowed = new LinkedHashSet<>();

        for (AuthPermissionQueryRepository.EffectivePermissionRow row : authPermissionQueryRepository.findEffectivePermissionsByUsername(username)) {
            String permissionCode = row.permissionCode();
            if (permissionCode == null || permissionCode.isBlank()) {
                continue;
            }
            permissionCodes.add(permissionCode);

            if (SOURCE_ACCOUNT_OVERRIDE.equalsIgnoreCase(row.sourceType())) {
                if (EFFECT_DENY.equalsIgnoreCase(row.effect())) {
                    accountDenied.add(permissionCode);
                } else if (EFFECT_ALLOW.equalsIgnoreCase(row.effect())) {
                    accountAllowed.add(permissionCode);
                }
            } else if (SOURCE_ROLE.equalsIgnoreCase(row.sourceType())
                    && EFFECT_ALLOW.equalsIgnoreCase(row.effect())) {
                roleAllowed.add(permissionCode);
            }
        }

        Set<String> allowed = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            if (accountDenied.contains(permissionCode)) {
                continue;
            }
            if (accountAllowed.contains(permissionCode) || roleAllowed.contains(permissionCode)) {
                allowed.add(permissionCode);
            }
        }

        return List.copyOf(allowed);
    }
}
