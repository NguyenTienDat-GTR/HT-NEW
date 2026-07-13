package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import phucuong.ht.common.enums.AuthProvider;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.LoginRequest;
import phucuong.ht.modules.auth.model.request.ChangePasswordRequest;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.model.response.AuthenticatedUserResponse;
import phucuong.ht.modules.auth.model.response.BootstrapSuperAdminResponse;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final AuthUserDetailsService authUserDetailsService;
    private final jwtUtils jwtUtils;
    private final AuthProperties authProperties;
    private final RedisTokenSessionService redisTokenSessionService;

    @Transactional
    public BootstrapSuperAdminResponse bootstrapSuperAdmin() {
        String username = authProperties.getBootstrap().getSuperAdmin().getUsername();
        if (accountRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Tài khoản super admin đã tồn tại");
        }

        Role role = roleRepository.findByRoleCodeAndStatusTrue(authProperties.getBootstrap().getSuperAdmin().getRoleCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Vai trò SUPER_ADMIN hiện không khả dụng"));

        OffsetDateTime now = OffsetDateTime.now();
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(authProperties.getBootstrap().getSuperAdmin().getPassword()));
        account.setAuthProvider(AuthProvider.LOCAL.name());
        account.setMustChangePassword(Boolean.FALSE);
        account.setStatus(Boolean.TRUE);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(SYSTEM_ACTOR);
        account.setUpdatedBy(SYSTEM_ACTOR);
        Account savedAccount = accountRepository.save(account);

        AccountRole accountRole = new AccountRole();
        accountRole.setAccount(savedAccount);
        accountRole.setRole(role);
        accountRole.setIsPrimary(Boolean.TRUE);
        accountRole.setAssignedBy(SYSTEM_ACTOR);
        accountRole.setAssignedAt(now);
        accountRole.setStatus(Boolean.TRUE);
        accountRole.setCreatedAt(now);
        accountRole.setUpdatedAt(now);
        accountRole.setCreatedBy(SYSTEM_ACTOR);
        accountRole.setUpdatedBy(SYSTEM_ACTOR);
        accountRoleRepository.save(accountRole);

        AuthUserDetails principal = authUserDetailsService.loadUserByUsername(username);
        return BootstrapSuperAdminResponse.builder()
                .username(username)
                .roleCode(role.getRoleCode())
                .tokens(issueNewSessionTokens(principal, "bootstrap"))
                .build();
    }

    public AuthTokenResponse login(LoginRequest request, String ipAddress) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AuthUserDetails principal = (AuthUserDetails) authentication.getPrincipal();
        return issueNewSessionTokens(principal, normalizeIpAddress(ipAddress));
    }

    public AuthTokenResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.INVALID_TOKEN, "Refresh token là bắt buộc");
        }

        jwtUtils.TokenClaims refreshClaims = jwtUtils.parseRefreshTokenClaims(refreshToken);
        AuthUserDetails currentPrincipal = authUserDetailsService.loadUserByUsername(refreshClaims.principal().getUsername());
        return rotateSessionTokens(currentPrincipal, refreshClaims, refreshToken);
    }

    public AuthenticatedUserResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Yêu cầu xác thực");
        }
        return toUserResponse(principal);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Yêu cầu xác thực");
        }
        Account account = accountRepository.findById(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản"));
        if (!passwordEncoder.matches(request.getOldPassword(), account.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu cũ không chính xác");
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mật khẩu mới phải khác mật khẩu cũ");
        }
        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        account.setMustChangePassword(Boolean.FALSE);
        account.setUpdatedBy(principal.getUsername());
        accountRepository.save(account);
        redisTokenSessionService.revokeAllSessions(principal.getUsername());
    }

    public AuthenticatedUserResponse adminSummary(Authentication authentication) {
        return currentUser(authentication);
    }

    private AuthTokenResponse issueNewSessionTokens(AuthUserDetails principal, String ipAddress) {
        String accessTokenJti = UUID.randomUUID().toString();
        String refreshTokenJti = UUID.randomUUID().toString();
        TokenSessionState sessionState = redisTokenSessionService.createSession(
                principal.getUsername(),
                ipAddress,
                accessTokenJti,
                refreshTokenJti,
                "pending"
        );

        String accessToken = jwtUtils.generateAccessToken(principal, sessionState.getSessionId(), accessTokenJti);
        String refreshToken = jwtUtils.generateRefreshToken(principal, sessionState.getSessionId(), refreshTokenJti);
        redisTokenSessionService.rotateSession(
                sessionState.getSessionId(),
                principal.getUsername(),
                accessTokenJti,
                refreshTokenJti,
                refreshToken
        );
        return buildTokenResponse(principal, accessToken, refreshToken);
    }

    private AuthTokenResponse rotateSessionTokens(
            AuthUserDetails principal,
            jwtUtils.TokenClaims previousRefreshClaims,
            String previousRefreshToken
    ) {
        String accessTokenJti = UUID.randomUUID().toString();
        String refreshTokenJti = UUID.randomUUID().toString();
        String accessToken = jwtUtils.generateAccessToken(principal, previousRefreshClaims.sessionId(), accessTokenJti);
        String refreshToken = jwtUtils.generateRefreshToken(principal, previousRefreshClaims.sessionId(), refreshTokenJti);
        redisTokenSessionService.rotateRefreshSession(
                previousRefreshClaims.sessionId(),
                principal.getUsername(),
                previousRefreshClaims.tokenId(),
                previousRefreshToken,
                accessTokenJti,
                refreshTokenJti,
                refreshToken
        );
        return buildTokenResponse(principal, accessToken, refreshToken);
    }

    private AuthTokenResponse buildTokenResponse(AuthUserDetails principal, String accessToken, String refreshToken) {
        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accessTokenExpiresInMs(jwtUtils.getAccessTokenExpirationMs())
                .refreshTokenExpiresInMs(jwtUtils.getRefreshTokenExpirationMs())
                .user(toUserResponse(principal))
                .build();
    }

    private String normalizeIpAddress(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Không thể xác định địa chỉ IP đăng nhập");
        }
        return ipAddress.trim();
    }

    private AuthenticatedUserResponse toUserResponse(AuthUserDetails principal) {
        return AuthenticatedUserResponse.builder()
                .username(principal.getUsername())
                .leaderId(principal.getLeaderId())
                .roles(principal.getRoles())
                .permissions(principal.getPermissions())
                .mustChangePassword(principal.isMustChangePassword())
                .build();
    }
}
