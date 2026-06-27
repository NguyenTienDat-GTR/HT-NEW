package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.AuthProvider;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.model.request.LoginRequest;
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

    @Transactional
    public BootstrapSuperAdminResponse bootstrapSuperAdmin() {
        String username = authProperties.getBootstrap().getSuperAdmin().getUsername();
        if (accountRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Super admin account already exists");
        }

        Role role = roleRepository.findByRoleCodeAndStatusTrue(authProperties.getBootstrap().getSuperAdmin().getRoleCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "SUPER_ADMIN role is not available"));

        OffsetDateTime now = OffsetDateTime.now();
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(authProperties.getBootstrap().getSuperAdmin().getPassword()));
        account.setAuthProvider(AuthProvider.LOCAL.name());
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
                .tokens(issueTokens(principal))
                .build();
    }

    public AuthTokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AuthUserDetails principal = (AuthUserDetails) authentication.getPrincipal();
        return issueTokens(principal);
    }

    public AuthTokenResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.INVALID_TOKEN, "Refresh token is required");
        }
        AuthUserDetails tokenPrincipal = jwtUtils.parseRefreshToken(refreshToken);
        AuthUserDetails currentPrincipal = authUserDetailsService.loadUserByUsername(tokenPrincipal.getUsername());
        return issueTokens(currentPrincipal);
    }

    public AuthenticatedUserResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, errorCode.AUTHENTICATION_FAILED, "Authentication is required");
        }
        return toUserResponse(principal);
    }

    public AuthenticatedUserResponse adminSummary(Authentication authentication) {
        return currentUser(authentication);
    }

    private AuthTokenResponse issueTokens(AuthUserDetails principal) {
        return AuthTokenResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(principal))
                .refreshToken(jwtUtils.generateRefreshToken(principal))
                .tokenType("Bearer")
                .accessTokenExpiresInMs(jwtUtils.getAccessTokenExpirationMs())
                .refreshTokenExpiresInMs(jwtUtils.getRefreshTokenExpirationMs())
                .user(toUserResponse(principal))
                .build();
    }

    private AuthenticatedUserResponse toUserResponse(AuthUserDetails principal) {
        return AuthenticatedUserResponse.builder()
                .username(principal.getUsername())
                .leaderId(principal.getLeaderId())
                .roles(principal.getRoles())
                .permissions(principal.getPermissions())
                .build();
    }
}
