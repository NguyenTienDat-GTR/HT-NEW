package phucuong.ht.modules.auth.model.request;

import phucuong.ht.modules.privacy.model.SelfConsentRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tự sửa thông tin cá nhân. Trường null = giữ nguyên. Các trường sửa được giống khi quản trị sửa hồ sơ huynh trưởng
 * (tên thánh, giới tính, email, số điện thoại, ảnh đại diện); không có họ và tên, giáo xứ, giáo hạt: những thứ đó do quản trị viên quản lý.
 * Ngày sinh chỉ đổi được khi tài khoản có quyền sửa ngày sinh (organization.leader.update_birth_date.*), như nút "Sửa ngày sinh" ở hồ sơ huynh trưởng.
 * Email trùng và số điện thoại trùng chỉ được kiểm tra khi giá trị thật sự thay đổi.
 */
@Getter
@Setter
public class MyProfileUpdateRequest extends SelfConsentRequest {

    @Size(max = 50, message = "Tên thánh tối đa 50 ký tự")
    private String holyName;

    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    private LocalDate birthDate;

    private LeaderGender gender;

    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải là số điện thoại Việt Nam gồm 10 chữ số và bắt đầu bằng 0")
    private String phoneNumber;

    /** Id ảnh đại diện mới đã tải lên qua POST /api/media (category LEADER_AVATAR). */
    private UUID imageAssetId;

    private Boolean clearImage;

    /** Bắt buộc khi đổi email (email nhận OTP đổi / quên mật khẩu). */
    private String currentPassword;
}
