package phucuong.ht.modules.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;
}
