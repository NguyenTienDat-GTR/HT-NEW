package phucuong.ht.modules.auth.model.request;

import phucuong.ht.modules.privacy.model.SelfConsentRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailUpdateRequest extends SelfConsentRequest {

    @NotBlank(message = "Vui lòng nhập email mới")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String currentPassword;
}
