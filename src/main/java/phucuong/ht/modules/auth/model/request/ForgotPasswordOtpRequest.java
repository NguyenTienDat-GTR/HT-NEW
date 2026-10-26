package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordOtpRequest {

    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Size(max = 100, message = "Tên đăng nhập không hợp lệ")
    private String username;
}
