package phucuong.ht.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.model.request.EmailUpdateRequest;
import phucuong.ht.modules.auth.model.response.EmailUpdateResponse;
import phucuong.ht.modules.auth.service.MyEmailService;
import phucuong.ht.modules.auth.model.request.PasswordChangeConfirmRequest;
import phucuong.ht.modules.auth.model.request.PasswordOtpSendRequest;
import phucuong.ht.modules.auth.model.request.PasswordOtpVerifyRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificatePreviewResponse;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.auth.model.response.MyProfileResponse;
import phucuong.ht.modules.auth.model.response.PasswordOtpSendResponse;
import phucuong.ht.modules.auth.model.response.PasswordOtpVerifyResponse;
import phucuong.ht.modules.auth.service.AuthCookieService;
import phucuong.ht.modules.auth.service.MyProfileService;
import phucuong.ht.modules.auth.service.MyPermissionService;
import phucuong.ht.modules.auth.model.response.MyPermissionResponse;
import phucuong.ht.modules.auth.service.PasswordChangeService;
import org.springframework.data.domain.Page;
import phucuong.ht.common.repository.BaseSearchRequest;

/** Chức năng tự phục vụ của tài khoản đang đăng nhập: hồ sơ cá nhân và đổi mật khẩu bằng OTP. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountSelfServiceController {

    private final MyProfileService myProfileService;
    private final MyEmailService myEmailService;
    private final MyPermissionService myPermissionService;
    private final PasswordChangeService passwordChangeService;
    private final AuthCookieService authCookieService;
    private final jwtUtils jwtUtils;

    @GetMapping("/me/profile")
    public apiResponse<MyProfileResponse> profile(Authentication authentication) {
        return apiResponse.success("Lấy hồ sơ cá nhân thành công", myProfileService.currentProfile(authentication));
    }

    @PutMapping("/me/email")
    public apiResponse<EmailUpdateResponse> updateEmail(@Valid @RequestBody EmailUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Cập nhật email thành công", myEmailService.update(request, authentication));
    }

    @GetMapping("/me/permissions")
    public apiResponse<Page<MyPermissionResponse>> myPermissions(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách quyền hiện có thành công", myPermissionService.search(request, authentication));
    }

    @GetMapping("/me/permissions/filter-options")
    public apiResponse<MyPermissionService.MyPermissionFilterOptions> myPermissionFilterOptions(Authentication authentication) {
        return apiResponse.success("Lấy giá trị lọc quyền thành công", myPermissionService.filterOptions(authentication));
    }

    @GetMapping("/me/certificates/{certificateCode}")
    public apiResponse<CertificateResponse> myCertificate(@PathVariable String certificateCode, Authentication authentication) {
        return apiResponse.success("Lấy chứng nhận thành công", myProfileService.myCertificate(certificateCode, authentication));
    }

    @GetMapping("/me/certificates/{certificateCode}/preview")
    public apiResponse<CertificatePreviewResponse> myCertificatePreview(@PathVariable String certificateCode, Authentication authentication) {
        return apiResponse.success("Lấy bản xem trước chứng nhận thành công", myProfileService.myCertificatePreview(certificateCode, authentication));
    }

    @PostMapping("/password/otp")
    public apiResponse<PasswordOtpSendResponse> sendOtp(
            @Valid @RequestBody PasswordOtpSendRequest request,
            Authentication authentication
    ) {
        PasswordOtpSendResponse response = passwordChangeService.sendOtp(request, authentication);
        return apiResponse.success("Đã gửi mã OTP tới " + response.getEmail(), response);
    }

    @PostMapping("/password/otp/verify")
    public apiResponse<PasswordOtpVerifyResponse> verifyOtp(
            @Valid @RequestBody PasswordOtpVerifyRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Xác thực OTP thành công", passwordChangeService.verifyOtp(request, authentication));
    }

    @PostMapping("/password/change")
    public apiResponse<Void> changePassword(
            @Valid @RequestBody PasswordChangeConfirmRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        passwordChangeService.changePassword(request, authentication, resolveCurrentSessionId(httpServletRequest));
        return apiResponse.success("Đổi mật khẩu thành công", null);
    }

    private String resolveCurrentSessionId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authCookieService.resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtUtils.parseAccessTokenClaims(token).sessionId();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
