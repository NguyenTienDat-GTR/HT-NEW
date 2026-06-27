package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthTokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long accessTokenExpiresInMs;
    private final long refreshTokenExpiresInMs;
    private final AuthenticatedUserResponse user;
}
