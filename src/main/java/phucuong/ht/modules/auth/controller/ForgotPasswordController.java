package phucuong.ht.modules.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.auth.model.request.ForgotPasswordOtpRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordResetRequest;
import phucuong.ht.modules.auth.model.request.ForgotPasswordVerifyRequest;
import phucuong.ht.modules.auth.model.response.ForgotPasswordOtpResponse;
import phucuong.ht.modules.auth.model.response.ForgotPasswordVerifyResponse;
import phucuong.ht.modules.auth.service.ForgotPasswordService;

/** Quên mật khẩu: các endpoint công khai (không cần đăng nhập), được giới hạn tần suất theo IP. */
@RestController
@RequestMapping("/api/auth/forgot-password")
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    @PostMapping("/otp")
    public apiResponse<ForgotPasswordOtpResponse> sendOtp(@Valid @RequestBody ForgotPasswordOtpRequest request) {
        ForgotPasswordOtpResponse response = forgotPasswordService.sendOtp(request);
        return apiResponse.success("Đã gửi mã OTP tới " + response.getMaskedEmail(), response);
    }

    @PostMapping("/otp/verify")
    public apiResponse<ForgotPasswordVerifyResponse> verifyOtp(@Valid @RequestBody ForgotPasswordVerifyRequest request) {
        return apiResponse.success("Xác thực OTP thành công", forgotPasswordService.verifyOtp(request));
    }

    @PostMapping("/reset")
    public apiResponse<Void> reset(@Valid @RequestBody ForgotPasswordResetRequest request) {
        forgotPasswordService.resetPassword(request);
        return apiResponse.success("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới", null);
    }
}
