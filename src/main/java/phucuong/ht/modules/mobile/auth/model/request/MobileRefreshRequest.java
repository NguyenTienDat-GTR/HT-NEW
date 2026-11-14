package phucuong.ht.modules.mobile.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Làm mới phiên của app di động: refresh token lấy từ thân JSON, không bao giờ đọc từ cookie. */
@Getter
@Setter
public class MobileRefreshRequest {

    @NotBlank(message = "Refresh token là bắt buộc")
    @Size(max = 4096, message = "Refresh token không hợp lệ")
    private String refreshToken;
}
