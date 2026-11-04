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
        // Phiên thường: hết hạn sau 8 giờ không hoạt động, tối đa 12 giờ kể từ lúc đăng nhập.
        private long sessionIdleMs = 8L * 60 * 60 * 1000;
        private long sessionAbsoluteMs = 12L * 60 * 60 * 1000;
        // Ghi nhớ đăng nhập: 7 ngày không hoạt động, tối đa 30 ngày kể từ lúc đăng nhập.
        private long rememberMeIdleMs = 7L * 24 * 60 * 60 * 1000;
        private long rememberMeAbsoluteMs = 30L * 24 * 60 * 60 * 1000;
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
        /**
         * true (dev): trả accessToken trong body JSON để dễ debug. false (test/production): accessToken chỉ nằm trong
         * cookie HttpOnly, không xuất hiện trong response body nên không đọc được bằng JS/DevTools Preview.
         */
        private boolean exposeAccessTokenInBody = true;
    }
}
