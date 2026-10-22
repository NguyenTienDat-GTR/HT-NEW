package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordOtpVerifyResponse {

    private final String changeToken;
    private final long expiresInSeconds;
}
