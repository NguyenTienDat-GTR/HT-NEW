package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordOtpSendRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String oldPassword;
}
