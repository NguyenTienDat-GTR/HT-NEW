package phucuong.ht.modules.leader.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin cá nhân huynh trưởng tự sửa trong hồ sơ của mình. Không gồm họ và tên / giáo xứ / giáo hạt / cấp độ.
 * Trường null = giữ nguyên. Tên thuộc tính trùng tên thuộc tính của Leader để BaseRepo sao chép tự động.
 * birthDate chỉ được điền khi người thao tác có quyền sửa ngày sinh (do MyProfileService kiểm tra).
 */
@Getter
@Setter
public class LeaderSelfUpdateRequest {

    private String holyName;
    private LocalDate birthDate;
    private LeaderGender gender;
    private String email;
    private String phoneNumber;

    /** Id ảnh đại diện mới đã tải lên qua POST /api/media; null = giữ nguyên ảnh. */
    private UUID imageAssetId;

    /** true = gỡ ảnh đại diện hiện tại. */
    private Boolean clearImage;
}
