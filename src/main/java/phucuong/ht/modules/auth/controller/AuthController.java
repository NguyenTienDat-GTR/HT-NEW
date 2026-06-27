package phucuong.ht.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.auth.model.request.LoginRequest;
import phucuong.ht.modules.auth.model.request.RefreshTokenRequest;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.model.response.AuthenticatedUserResponse;
import phucuong.ht.modules.auth.service.AuthCookieService;
import phucuong.ht.modules.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

//    @PostMapping("/bootstrap/super-admin")
//    public apiResponse<BootstrapSuperAdminResponse> bootstrapSuperAdmin(HttpServletResponse response) {
//        BootstrapSuperAdminResponse result = authService.bootstrapSuperAdmin();
//        authCookieService.writeAuthCookies(response, result.getTokens());
//        return apiResponse.success("Super admin bootstrap completed", result);
//    }

    @PostMapping("/login")
    public apiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthTokenResponse tokens = authService.login(request);
        authCookieService.writeAuthCookies(response, tokens);
        return apiResponse.success("Login successful", tokens);
    }

    @PostMapping("/refresh")
    public apiResponse<AuthTokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        RefreshTokenRequest resolvedRequest = request == null ? new RefreshTokenRequest() : request;
        if (!StringUtils.hasText(resolvedRequest.getRefreshToken())) {
            resolvedRequest.setRefreshToken(authCookieService.resolveRefreshToken(httpServletRequest));
        }
        AuthTokenResponse tokens = authService.refresh(resolvedRequest);
        authCookieService.writeAuthCookies(httpServletResponse, tokens);
        return apiResponse.success("Token refresh successful", tokens);
    }

    @GetMapping("/me")
    public apiResponse<AuthenticatedUserResponse> me(Authentication authentication) {
        return apiResponse.success("Authenticated user loaded", authService.currentUser(authentication));
    }

    @GetMapping("/admin/summary")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'auth.session.manage.all')")
    public apiResponse<AuthenticatedUserResponse> adminSummary(Authentication authentication) {
        return apiResponse.success("Authorized admin summary loaded", authService.adminSummary(authentication));
    }
}
