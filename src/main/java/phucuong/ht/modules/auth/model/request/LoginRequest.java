package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Tên đăng nhập là bắt buộc")
    private String username;

    @NotBlank(message = "Mật khẩu là bắt buộc")
    private String password;

    /** Tick "Ghi nhớ đăng nhập": phiên sống lâu hơn và cookie refresh được lưu trên trình duyệt. */
    private Boolean rememberMe;

    /** Định danh thiết bị do FE sinh và lưu cục bộ; phiên được phân biệt theo thiết bị thay vì theo IP. */
    @Size(max = 64, message = "Mã thiết bị không hợp lệ")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Mã thiết bị không hợp lệ")
    private String deviceId;

    /** Token Cloudflare Turnstile do widget trên form đăng nhập sinh ra; chỉ bắt buộc khi BE bật Turnstile. */
    @Size(max = 2048, message = "Mã xác minh không hợp lệ")
    private String turnstileToken;

    public boolean isRememberMeEnabled() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
