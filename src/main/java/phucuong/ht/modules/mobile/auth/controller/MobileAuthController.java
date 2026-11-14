package phucuong.ht.modules.mobile.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.auth.model.request.LoginRequest;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.service.AuthService;
import phucuong.ht.modules.auth.service.TurnstileService;
import phucuong.ht.modules.mobile.auth.model.request.MobileLogoutRequest;
import phucuong.ht.modules.mobile.auth.model.request.MobileRefreshRequest;
import phucuong.ht.modules.mobile.auth.model.response.MobileAuthTokenResponse;

/**
 * Đăng nhập / làm mới / đăng xuất cho app di động. Chỉ thêm mới, không đổi {@code /api/auth/*} của web.
 *
 * <p>Khác web: token nằm trong thân JSON (app lưu bằng Keychain/Keystore) và KHÔNG có cookie nào được đọc hay ghi.
 * Mã độc chạy trên web không đọc được cookie HttpOnly nên không có refresh token để gửi vào thân của endpoint này.
 * Captcha Turnstile, kiểm tra tên miền site, giới hạn tần suất và cơ chế xoay vòng refresh token dùng lại đúng
 * {@link AuthService} / {@link TurnstileService} của web.
 *
 * <p>Tắt bằng {@code app.mobile.auth.enabled=false} (mặc định bật): khi tắt, ba endpoint không tồn tại (404).
 */
@RestController
@RequestMapping("/api/mobile/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mobile.auth.enabled", havingValue = "true", matchIfMissing = true)
public class MobileAuthController {

    private final AuthService authService;
    private final TurnstileService turnstileService;

    @PostMapping("/login")
    public apiResponse<MobileAuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        noStore(httpServletResponse);
        turnstileService.assertHuman(request.getTurnstileToken(), httpServletRequest.getRemoteAddr());
        AuthTokenResponse tokens = authService.login(
                request, httpServletRequest.getRemoteAddr(), httpServletRequest.getHeader("X-Site-Host"));
        return apiResponse.success("Đăng nhập thành công", MobileAuthTokenResponse.from(tokens));
    }

    @PostMapping("/refresh")
    public apiResponse<MobileAuthTokenResponse> refresh(
            @Valid @RequestBody MobileRefreshRequest request,
            HttpServletResponse httpServletResponse
    ) {
        noStore(httpServletResponse);
        AuthTokenResponse tokens = authService.refresh(request.getRefreshToken());
        return apiResponse.success("Làm mới token thành công", MobileAuthTokenResponse.from(tokens));
    }

    @PostMapping("/logout")
    public apiResponse<Void> logout(
            @Valid @RequestBody(required = false) MobileLogoutRequest request,
            HttpServletResponse httpServletResponse
    ) {
        noStore(httpServletResponse);
        authService.logout(
                request == null ? null : request.getRefreshToken(),
                request == null ? null : request.getAccessToken());
        return apiResponse.success("Đăng xuất thành công", null);
    }

    /** Phản hồi chứa token không được lưu đệm ở proxy hay trình duyệt. */
    private void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
