package phucuong.ht.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    // Đăng nhập: chỉ đếm lần thất bại; refresh token và đổi/quên mật khẩu dùng chung nhóm auth.
    private Tier login = new Tier(10, 300);
    private Tier auth = new Tier(30, 60);
    // Quên mật khẩu (công khai): theo IP, 20 yêu cầu / 15 phút; giới hạn theo tài khoản nằm ở ForgotPasswordService.
    private Tier forgot = new Tier(20, 900);
    private Tier write = new Tier(30, 60);
    private Tier read = new Tier(120, 60);
    private Tier workflow = new Tier(20, 60);

    @Getter
    @Setter
    public static class Tier {
        private int limit;
        private int windowSeconds;

        public Tier() {
        }

        public Tier(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
