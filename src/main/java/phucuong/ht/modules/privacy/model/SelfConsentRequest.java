package phucuong.ht.modules.privacy.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Lớp cha cho request mà người dùng tự gửi về dữ liệu của chính mình: bắt buộc tích đồng ý (không có mặc định). */
@Getter
@Setter
public abstract class SelfConsentRequest {

    @NotNull(message = "Vui lòng đồng ý với chính sách bảo vệ dữ liệu cá nhân")
    @AssertTrue(message = "Vui lòng đồng ý với chính sách bảo vệ dữ liệu cá nhân")
    private Boolean privacyConsent;
}
