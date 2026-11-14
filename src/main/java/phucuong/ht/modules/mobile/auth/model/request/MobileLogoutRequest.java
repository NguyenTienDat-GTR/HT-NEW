package phucuong.ht.modules.mobile.auth.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Đăng xuất của app di động. Cả hai token đều tùy chọn (đăng xuất khi token đã hỏng vẫn phải xóa phiên phía app). */
@Getter
@Setter
public class MobileLogoutRequest {

    @Size(max = 4096, message = "Refresh token không hợp lệ")
    private String refreshToken;

    @Size(max = 4096, message = "Access token không hợp lệ")
    private String accessToken;
}
