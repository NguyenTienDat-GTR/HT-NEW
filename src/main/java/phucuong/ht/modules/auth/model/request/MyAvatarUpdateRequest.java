package phucuong.ht.modules.auth.model.request;

import phucuong.ht.modules.privacy.model.SelfConsentRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Đặt ảnh đại diện của tài khoản không gắn huynh trưởng. */
@Getter
@Setter
public class MyAvatarUpdateRequest extends SelfConsentRequest {

    /** Id ảnh đã tải lên qua POST /api/media (category ACCOUNT_AVATAR). */
    @NotNull(message = "Vui lòng chọn ảnh đại diện")
    private UUID imageAssetId;
}
