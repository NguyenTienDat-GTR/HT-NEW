package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthTokenResponse {

    private final String accessToken;
    @JsonIgnore
    private final String refreshToken;
    private final String tokenType;
    private final long accessTokenExpiresInMs;
    private final long refreshTokenExpiresInMs;
    private final boolean rememberMe;
    private final AuthenticatedUserResponse user;
}
