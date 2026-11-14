package phucuong.ht.modules.mobile.auth.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.auth.model.response.AuthTokenResponse;
import phucuong.ht.modules.auth.model.response.AuthenticatedUserResponse;

/**
 * Kết quả đăng nhập / làm mới của app di động: token nằm trong thân JSON (app lưu bằng Keychain/Keystore), không có cookie.
 * Khác {@link AuthTokenResponse} của web: có cả refreshToken và luôn có accessToken.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileAuthTokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long accessTokenExpiresInMs;
    private final long refreshTokenExpiresInMs;
    private final boolean rememberMe;
    private final AuthenticatedUserResponse user;

    public static MobileAuthTokenResponse from(AuthTokenResponse tokens) {
        return MobileAuthTokenResponse.builder()
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .tokenType(tokens.getTokenType())
                .accessTokenExpiresInMs(tokens.getAccessTokenExpiresInMs())
                .refreshTokenExpiresInMs(tokens.getRefreshTokenExpiresInMs())
                .rememberMe(tokens.isRememberMe())
                .user(tokens.getUser())
                .build();
    }
}
