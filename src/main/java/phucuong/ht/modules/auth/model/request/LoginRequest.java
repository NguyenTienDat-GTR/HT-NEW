package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
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

    public boolean isRememberMeEnabled() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
