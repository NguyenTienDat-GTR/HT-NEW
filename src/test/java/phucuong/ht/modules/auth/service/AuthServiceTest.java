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
import phucuong.ht.common.exception.ApiException;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private SiteLoginGuard siteLoginGuard;

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
        when(redisTokenSessionService.createSession(eq("superadminpc"), eq("bootstrap"), anyString(), anyString(), eq("pending"), anyBoolean()))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-1"), anyString())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-1"), anyString(), any(Instant.class))).thenReturn("refresh-token");
        when(redisTokenSessionService.rotateSession(eq("session-1"), eq("superadminpc"), anyString(), anyString(), eq("refresh-token")))
                .thenReturn(sessionState);
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);

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
        when(redisTokenSessionService.createSession(eq("superadminpc"), eq("127.0.0.1"), anyString(), anyString(), eq("pending"), anyBoolean()))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-2"), anyString())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-2"), anyString(), any(Instant.class))).thenReturn("refresh-token");
        when(redisTokenSessionService.rotateSession(eq("session-2"), eq("superadminpc"), anyString(), anyString(), eq("refresh-token")))
                .thenReturn(sessionState);
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);

        AuthTokenResponse response = authService.login(request, "127.0.0.1");

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("superadminpc", response.getUser().getUsername());
    }

    @Test
    void loginShouldKeySessionByDeviceIdWhenProvidedSoSameIpDevicesCoexist() {
        AuthService authService = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("superadminpc");
        request.setPassword("secret");
        request.setDeviceId("device-abc");
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "superadminpc", "encoded-password", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all"), true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        TokenSessionState sessionState = session("session-3", "superadminpc", "device:device-abc");

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(redisTokenSessionService.createSession(eq("superadminpc"), eq("device:device-abc"), anyString(), anyString(), eq("pending"), anyBoolean()))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-3"), anyString())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-3"), anyString(), any(Instant.class))).thenReturn("refresh-token");
        when(redisTokenSessionService.rotateSession(eq("session-3"), eq("superadminpc"), anyString(), anyString(), eq("refresh-token")))
                .thenReturn(sessionState);
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);

        AuthTokenResponse response = authService.login(request, "127.0.0.1");

        assertEquals("access-token", response.getAccessToken());
        verify(redisTokenSessionService).createSession(eq("superadminpc"), eq("device:device-abc"), anyString(), anyString(), eq("pending"), anyBoolean());
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
        jwtUtils.TokenClaims tokenClaims = new jwtUtils.TokenClaims(
                AuthUserDetails.tokenPrincipal("superadminpc", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all")),
                "session-3",
                "refresh-jti-old",
                Instant.now(),
                Instant.now().plusSeconds(600),
                jwtUtils.REFRESH_TOKEN_TYPE
        );

        TokenSessionState currentSession = session("session-3", "superadminpc", "127.0.0.1");
        when(redisTokenSessionService.findActiveSession("session-3")).thenReturn(currentSession);
        when(redisTokenSessionService.isRememberMe(currentSession)).thenReturn(true);
        when(redisTokenSessionService.nextExpiry(currentSession)).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtUtils.parseRefreshTokenClaims("refresh-token-old")).thenReturn(tokenClaims);
        when(authUserDetailsService.loadUserByUsername("superadminpc")).thenReturn(principal);
        when(jwtUtils.generateAccessToken(eq(principal), eq("session-3"), anyString())).thenReturn("access-token-new");
        when(jwtUtils.generateRefreshToken(eq(principal), eq("session-3"), anyString(), any(Instant.class))).thenReturn("refresh-token-new");
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);

        AuthTokenResponse response = authService.refresh("refresh-token-old");

        assertEquals("access-token-new", response.getAccessToken());
        assertEquals("refresh-token-new", response.getRefreshToken());
        verify(redisTokenSessionService).rotateRefreshSession(
                eq("session-3"),
                eq("superadminpc"),
                eq("refresh-jti-old"),
                eq("refresh-token-old"),
                anyString(),
                anyString(),
                eq("refresh-token-new")
        );
    }

    @Test
    void refreshShouldRejectMissingToken() {
        AuthService authService = createService();
        assertThrows(ApiException.class, () -> authService.refresh(null));
    }

    @Test
    void currentUserShouldReloadPermissionsFromDatabaseInsteadOfTokenSnapshot() {
        AuthService authService = createService();
        AuthUserDetails tokenPrincipal = AuthUserDetails.tokenPrincipal(
                "admin-diocese",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of("system.account.manage.diocese")
        );
        AuthUserDetails databasePrincipal = AuthUserDetails.databasePrincipal(
                "admin-diocese",
                "encoded-password",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of("system.account.manage.diocese", "system.account_role.assign.diocese"),
                true
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(tokenPrincipal, null, tokenPrincipal.getAuthorities());
        when(authUserDetailsService.loadUserByUsername("admin-diocese")).thenReturn(databasePrincipal);

        var response = authService.currentUser(authentication);

        assertEquals(List.of("system.account.manage.diocese", "system.account_role.assign.diocese"), response.getPermissions());
    }

    @Test
    void logoutShouldRevokeOnlyTheRefreshTokenSession() {
        AuthService authService = createService();
        jwtUtils.TokenClaims tokenClaims = new jwtUtils.TokenClaims(
                AuthUserDetails.tokenPrincipal("superadminpc", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all")),
                "session-logout",
                "refresh-jti",
                Instant.now(),
                Instant.now().plusSeconds(600),
                jwtUtils.REFRESH_TOKEN_TYPE
        );

        when(jwtUtils.parseRefreshTokenClaims("refresh-token")).thenReturn(tokenClaims);

        authService.logout("refresh-token", "access-token");

        verify(redisTokenSessionService).revokeSession("session-logout");
        verify(redisTokenSessionService, never()).revokeAllSessions(anyString());
        verify(jwtUtils, never()).parseAccessTokenClaims(anyString());
    }

    @Test
    void logoutShouldFallbackToAccessTokenSessionWhenRefreshTokenIsMissing() {
        AuthService authService = createService();
        jwtUtils.TokenClaims tokenClaims = new jwtUtils.TokenClaims(
                AuthUserDetails.tokenPrincipal("superadminpc", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all")),
                "session-from-access",
                "access-jti",
                Instant.now(),
                Instant.now().plusSeconds(600),
                jwtUtils.ACCESS_TOKEN_TYPE
        );

        when(jwtUtils.parseAccessTokenClaims("access-token")).thenReturn(tokenClaims);

        authService.logout(null, "access-token");

        verify(redisTokenSessionService).revokeSession("session-from-access");
        verify(redisTokenSessionService, never()).revokeAllSessions(anyString());
    }

    @Test
    void logoutShouldIgnoreMissingTokens() {
        AuthService authService = createService();

        authService.logout(null, null);

        verifyNoInteractions(jwtUtils, redisTokenSessionService);
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

    @Test
    void loginWithoutRememberMeShouldCreateShortSessionAndSessionCookieFlag() {
        AuthService authService = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("superadminpc");
        request.setPassword("secret");
        AuthUserDetails principal = stubLogin("session-4");

        AuthTokenResponse response = authService.login(request, "127.0.0.1");

        verify(redisTokenSessionService).createSession(
                eq("superadminpc"), eq("127.0.0.1"), anyString(), anyString(), eq("pending"), eq(false));
        assertFalse(response.isRememberMe());
        assertEquals(principal.getUsername(), response.getUser().getUsername());
    }

    @Test
    void loginWithRememberMeShouldCreateLongSession() {
        AuthService authService = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("superadminpc");
        request.setPassword("secret");
        request.setRememberMe(true);
        stubLogin("session-5");

        AuthTokenResponse response = authService.login(request, "127.0.0.1");

        verify(redisTokenSessionService).createSession(
                eq("superadminpc"), eq("127.0.0.1"), anyString(), anyString(), eq("pending"), eq(true));
        assertTrue(response.isRememberMe());
    }

    @Test
    void loginShouldRejectExpiredTemporaryPassword() {
        AuthService authService = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("admin-parish");
        request.setPassword("TempPass");
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "admin-parish", "hash", null, List.of("ADMIN_PARISH"), List.of(), true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        Account account = new Account();
        account.setUsername("admin-parish");
        account.setMustChangePassword(Boolean.TRUE);
        account.setTemporaryPasswordExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(accountRepository.findById("admin-parish")).thenReturn(Optional.of(account));

        assertThrows(ApiException.class, () -> authService.login(request, "127.0.0.1"));
        verifyNoInteractions(redisTokenSessionService);
    }

    @Test
    void loginShouldAcceptTemporaryPasswordBeforeItExpires() {
        AuthService authService = createService();
        LoginRequest request = new LoginRequest();
        request.setUsername("superadminpc");
        request.setPassword("TempPass");
        stubLogin("session-6");
        Account account = new Account();
        account.setUsername("superadminpc");
        account.setMustChangePassword(Boolean.TRUE);
        account.setTemporaryPasswordExpiresAt(OffsetDateTime.now().plusMinutes(5));
        when(accountRepository.findById("superadminpc")).thenReturn(Optional.of(account));

        AuthTokenResponse response = authService.login(request, "127.0.0.1");

        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void refreshShouldRejectWhenSessionIsGone() {
        AuthService authService = createService();
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "superadminpc", "encoded-password", null, List.of("SUPER_ADMIN"), List.of(), true);
        jwtUtils.TokenClaims tokenClaims = new jwtUtils.TokenClaims(
                AuthUserDetails.tokenPrincipal("superadminpc", null, List.of("SUPER_ADMIN"), List.of()),
                "session-gone",
                "refresh-jti-old",
                Instant.now(),
                Instant.now().plusSeconds(600),
                jwtUtils.REFRESH_TOKEN_TYPE
        );
        when(jwtUtils.parseRefreshTokenClaims("refresh-token-old")).thenReturn(tokenClaims);
        when(authUserDetailsService.loadUserByUsername("superadminpc")).thenReturn(principal);
        when(redisTokenSessionService.findActiveSession("session-gone")).thenReturn(null);

        assertThrows(ApiException.class, () -> authService.refresh("refresh-token-old"));
        verify(redisTokenSessionService, never()).rotateRefreshSession(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private AuthUserDetails stubLogin(String sessionId) {
        AuthUserDetails principal = AuthUserDetails.databasePrincipal(
                "superadminpc", "encoded-password", null, List.of("SUPER_ADMIN"), List.of("auth.session.manage.all"), true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        TokenSessionState sessionState = session(sessionId, "superadminpc", "127.0.0.1");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(redisTokenSessionService.createSession(
                eq("superadminpc"), eq("127.0.0.1"), anyString(), anyString(), eq("pending"), anyBoolean()))
                .thenReturn(sessionState);
        when(jwtUtils.generateAccessToken(eq(principal), eq(sessionId), anyString())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(eq(principal), eq(sessionId), anyString(), any(Instant.class))).thenReturn("refresh-token");
        when(jwtUtils.getAccessTokenExpirationMs()).thenReturn(900000L);
        return principal;
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
                redisTokenSessionService,
                siteLoginGuard
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
                now,
                true,
                now.plusSeconds(3600)
        );
    }
}
