package phucuong.ht.common.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusToggleRequest {

    @NotBlank(message = "Xác nhận là bắt buộc")
    private String confirmationText;
}
