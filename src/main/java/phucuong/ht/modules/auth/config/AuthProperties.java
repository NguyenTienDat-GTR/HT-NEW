package phucuong.ht.modules.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Bootstrap bootstrap = new Bootstrap();
    private final Cookie cookie = new Cookie();

    @Getter
    @Setter
    public static class Jwt {
        private String accessSecret;
        private String refreshSecret;
        private long accessTokenExpirationMs;
        private long refreshTokenExpirationMs;
        private String issuer;
        private String audience;
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private final SuperAdmin superAdmin = new SuperAdmin();
    }

    @Getter
    @Setter
    public static class SuperAdmin {
        private String username;
        private String password;
        private String roleCode;
    }

    @Getter
    @Setter
    public static class Cookie {
        private String accessTokenName = "accessToken";
        private String refreshTokenName = "refreshToken";
        private String path = "/";
        private String sameSite = "Lax";
        private boolean secure = false;
    }
}
