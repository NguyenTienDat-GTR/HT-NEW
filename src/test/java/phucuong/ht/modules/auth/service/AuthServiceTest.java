package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthUserDetailsService authUserDetailsService;

    @Mock
    private jwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @Test
    void bootstrapSuperAdminShouldAttachSavedAccountToRoleAssignment() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getBootstrap().getSuperAdmin().setUsername("superadminpc");
        authProperties.getBootstrap().getSuperAdmin().setPassword("htgppc2026");
        authProperties.getBootstrap().getSuperAdmin().setRoleCode("SUPER_ADMIN");
        authService = new AuthService(
                authenticationManager,
                passwordEncoder,
                accountRepository,
                accountRoleRepository,
                roleRepository,
                authUserDetailsService,
                jwtUtils,
                authProperties
        );

        Role role = new Role();
        role.setRoleCode("SUPER_ADMIN");

        Account savedAccount = new Account();
        savedAccount.setUsername("superadminpc");

        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "superadminpc",
                "encoded-password",
                null,
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all"),
                true
        );

        when(accountRepository.existsByUsername("superadminpc")).thenReturn(false);
        when(roleRepository.findByRoleCodeAndStatusTrue("SUPER_ADMIN")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("htgppc2026")).thenReturn("encoded-password");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(authUserDetailsService.loadUserByUsername("superadminpc")).thenReturn(principal);
        when(jwtUtils.generateAccessToken(principal)).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(principal)).thenReturn("refresh-token");
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtUtils.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        authService.bootstrapSuperAdmin();

        ArgumentCaptor<AccountRole> accountRoleCaptor = ArgumentCaptor.forClass(AccountRole.class);
        verify(accountRoleRepository).save(accountRoleCaptor.capture());
        assertSame(savedAccount, accountRoleCaptor.getValue().getAccount());
    }
}
