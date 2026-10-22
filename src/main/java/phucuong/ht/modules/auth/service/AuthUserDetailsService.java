package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import phucuong.ht.common.security.UnitOperationalStatusService;
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
    private final UnitOperationalStatusService unitOperationalStatusService;

    @Override
    public AuthUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + username));

        boolean unitLocked = unitOperationalStatusService.isAccountUnitLocked(username);
        List<String> roles = unitLocked ? List.of() : accountRoleRepository.findActiveRoleCodesByUsername(username);
        List<String> permissions = unitLocked ? List.of() : resolveAllowedPermissions(username);

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
        Set<String> allowed = new LinkedHashSet<>();
        for (AuthPermissionQueryRepository.EffectivePermissionRow row : authPermissionQueryRepository.findEffectivePermissionsByUsername(username)) {
            String permissionCode = row.permissionCode();
            if (permissionCode != null && !permissionCode.isBlank()) {
                allowed.add(permissionCode);
            }
        }
        return List.copyOf(allowed);
    }
}
