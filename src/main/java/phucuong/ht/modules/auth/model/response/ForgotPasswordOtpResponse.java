package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ForgotPasswordOtpResponse {

    /** Email đã che (ví dụ ng***@g***.com) để người dùng biết OTP được gửi tới đâu. */
    private final String maskedEmail;
    private final long expiresInSeconds;
    private final long resendAfterSeconds;
}
