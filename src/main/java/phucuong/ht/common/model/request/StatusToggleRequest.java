package phucuong.ht.common.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusToggleRequest {

    @NotBlank(message = "Xác nhận là bắt buộc")
    private String confirmationText;

    // Lý do bật/tắt đơn vị tổ chức; bắt buộc khi tắt (kiểm tra ở service).
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
