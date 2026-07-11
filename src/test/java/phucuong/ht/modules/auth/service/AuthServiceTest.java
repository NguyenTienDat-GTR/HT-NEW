package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.LoginRequest;
import phucuong.ht.modules.auth.model.request.ChangePasswordRequest;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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

    @Mock
    private RedisTokenSessionService redisTokenSessionService;

    @Test
    void bootstrapSuperAdminShouldAttachSavedAccountToRoleAssignment() {
        AuthService authService = createService();

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
        TokenSessionState sessionState = session("session-1", "superadminpc", "bootstrap");

        when(accountRepository.existsByUsername("superadminpc")).thenReturn(false);
        when(roleRepository.findByRoleCodeAndStatusTrue("SUPER_ADMIN")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("htgppc2026")).thenReturn("encoded-password");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(authUserDetailsService.loadUserByUsername("superadminpc")).thenReturn(principal);
        when(redisTokenSessionService.createSession(eq("superadminpc"), eq("bootstrap"), anyString(), anyString(), eq("pending")))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-1"), anyString())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-1"), anyString())).thenReturn("refresh-token");
        when(redisTokenSessionService.rotateSession(eq("session-1"), eq("superadminpc"), anyString(), anyString(), eq("refresh-token")))
                .thenReturn(sessionState);
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtUtils.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        authService.bootstrapSuperAdmin();

        ArgumentCaptor<AccountRole> accountRoleCaptor = ArgumentCaptor.forClass(AccountRole.class);
        verify(accountRoleRepository).save(accountRoleCaptor.capture());
        assertSame(savedAccount, accountRoleCaptor.getValue().getAccount());
    }

    @Test
    void loginShouldCreateRedisBackedSessionForCallerIp() {
        AuthService authService = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("superadminpc");
        request.setPassword("secret");

        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "superadminpc",
                "encoded-password",
                null,
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all"),
                true
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        TokenSessionState sessionState = session("session-2", "superadminpc", "127.0.0.1");

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(redisTokenSessionService.createSession(eq("superadminpc"), eq("127.0.0.1"), anyString(), anyString(), eq("pending")))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-2"), anyString())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-2"), anyString())).thenReturn("refresh-token");
        when(redisTokenSessionService.rotateSession(eq("session-2"), eq("superadminpc"), anyString(), anyString(), eq("refresh-token")))
                .thenReturn(sessionState);
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtUtils.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        AuthTokenResponse response = authService.login(request, "127.0.0.1");

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("superadminpc", response.getUser().getUsername());
    }

    @Test
    void refreshShouldRotateTokensForExistingRedisSession() {
        AuthService authService = createService();
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "superadminpc",
                "encoded-password",
                null,
                List.of("SUPER_ADMIN"),
                List.of("auth.session.manage.all"),
                true
        );
        TokenSessionState sessionState = session("session-3", "superadminpc", "127.0.0.1");
        jwtUtils.TokenClaims tokenClaims = new jwtUtils.TokenClaims(
                AuthUserDetails.tokenPrincipal("superadminpc", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all")),
                "session-3",
                "refresh-jti-old",
                Instant.now(),
                Instant.now().plusSeconds(600),
                jwtUtils.REFRESH_TOKEN_TYPE
        );

        when(jwtUtils.parseRefreshTokenClaims("refresh-token-old")).thenReturn(tokenClaims);
        when(authUserDetailsService.loadUserByUsername("superadminpc")).thenReturn(principal);
        when(redisTokenSessionService.requireRefreshSession("session-3", "superadminpc", "refresh-jti-old", "refresh-token-old"))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-3"), anyString())).thenReturn("access-token-new");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-3"), anyString())).thenReturn("refresh-token-new");
        when(redisTokenSessionService.rotateSession(eq("session-3"), eq("superadminpc"), anyString(), anyString(), eq("refresh-token-new")))
                .thenReturn(sessionState);
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtUtils.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        AuthTokenResponse response = authService.refresh("refresh-token-old");

        assertEquals("access-token-new", response.getAccessToken());
        assertEquals("refresh-token-new", response.getRefreshToken());
    }

    @Test
    void refreshShouldRejectMissingToken() {
        AuthService authService = createService();
        assertThrows(ApiException.class, () -> authService.refresh(null));
    }

    @Test
    void changePasswordShouldHashNewPasswordAndRevokeAllSessions() {
        AuthService authService = createService();
        Account account = new Account();
        account.setUsername("admin-parish");
        account.setPassword("old-hash");
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "admin-parish", "old-hash", null, List.of("ADMIN_PARISH"), List.of(), true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("old-password");
        request.setNewPassword("new-password");
        when(accountRepository.findById("admin-parish")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        authService.changePassword(request, authentication);

        assertEquals("new-hash", account.getPassword());
        verify(accountRepository).save(account);
        verify(redisTokenSessionService).revokeAllSessions("admin-parish");
    }

    @Test
    void changePasswordShouldRejectWrongOldPassword() {
        AuthService authService = createService();
        Account account = new Account();
        account.setUsername("admin-parish");
        account.setPassword("old-hash");
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "admin-parish", "old-hash", null, List.of("ADMIN_PARISH"), List.of(), true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrong-password");
        request.setNewPassword("new-password");
        when(accountRepository.findById("admin-parish")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThrows(ApiException.class, () -> authService.changePassword(request, authentication));
        verify(accountRepository, never()).save(any(Account.class));
        verify(redisTokenSessionService, never()).revokeAllSessions(anyString());
    }

    private AuthService createService() {
        return new AuthService(
                authenticationManager,
                passwordEncoder,
                accountRepository,
                accountRoleRepository,
                roleRepository,
                authUserDetailsService,
                jwtUtils,
                authProperties(),
                redisTokenSessionService
        );
    }

    private AuthProperties authProperties() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getBootstrap().getSuperAdmin().setUsername("superadminpc");
        authProperties.getBootstrap().getSuperAdmin().setPassword("htgppc2026");
        authProperties.getBootstrap().getSuperAdmin().setRoleCode("SUPER_ADMIN");
        return authProperties;
    }

    private TokenSessionState session(String sessionId, String username, String ipAddress) {
        Instant now = Instant.now();
        return new TokenSessionState(
                sessionId,
                username,
                ipAddress,
                "access-jti",
                new java.util.ArrayList<>(),
                "refresh-jti",
                "hash",
                now,
                now.plusSeconds(600),
                now
        );
    }
}
