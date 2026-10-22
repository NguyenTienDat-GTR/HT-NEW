package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordOtpSendResponse {

    private final String email;
    private final long expiresInSeconds;
    private final long resendAfterSeconds;
}
