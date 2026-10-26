package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Kết quả đặt lại mật khẩu: không chứa mật khẩu tạm (chỉ gửi qua email), chỉ có email đã che và hạn dùng. */
@Getter
@Builder
public class AccountPasswordResetResponse {

    private final String username;
    private final String maskedEmail;
    private final OffsetDateTime expiresAt;
}
