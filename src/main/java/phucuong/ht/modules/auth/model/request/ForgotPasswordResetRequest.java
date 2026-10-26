package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordResetRequest {

    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Size(max = 100, message = "Tên đăng nhập không hợp lệ")
    private String username;

    @NotBlank(message = "Phiên khôi phục mật khẩu không hợp lệ")
    private String resetToken;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 8, max = 72, message = "Mật khẩu mới phải từ 8 đến 72 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
    private String confirmPassword;
}
