package phucuong.ht.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.auth.model.request.LoginRequest;
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

    @PostMapping("/login")
    public apiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        AuthTokenResponse tokens = authService.login(request, httpServletRequest.getRemoteAddr());
        authCookieService.writeRefreshCookie(httpServletResponse, tokens);
        return apiResponse.success("Đăng nhập thành công", tokens);
    }

    @PostMapping("/refresh")
    public apiResponse<AuthTokenResponse> refresh(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        String refreshToken = authCookieService.resolveRefreshToken(httpServletRequest);
        AuthTokenResponse tokens = authService.refresh(refreshToken);
        authCookieService.writeRefreshCookie(httpServletResponse, tokens);
        return apiResponse.success("Làm mới token thành công", tokens);
    }

    @GetMapping("/me")
    public apiResponse<AuthenticatedUserResponse> me(Authentication authentication) {
        return apiResponse.success("Lấy thông tin người dùng đã xác thực thành công", authService.currentUser(authentication));
    }

    @GetMapping("/admin/summary")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'auth.session.manage.all')")
    public apiResponse<AuthenticatedUserResponse> adminSummary(Authentication authentication) {
        return apiResponse.success("Lấy thông tin tổng quan quản trị thành công", authService.adminSummary(authentication));
    }
}
