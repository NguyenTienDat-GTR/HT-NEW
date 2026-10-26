package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ForgotPasswordVerifyResponse {

    private final String resetToken;
    private final long expiresInSeconds;
}
