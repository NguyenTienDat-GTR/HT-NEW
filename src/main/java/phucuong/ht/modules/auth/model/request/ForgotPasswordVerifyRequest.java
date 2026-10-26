package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordVerifyRequest {

    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Size(max = 100, message = "Tên đăng nhập không hợp lệ")
    private String username;

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "[0-9]{6}", message = "Mã OTP gồm 6 chữ số")
    private String otp;
}
