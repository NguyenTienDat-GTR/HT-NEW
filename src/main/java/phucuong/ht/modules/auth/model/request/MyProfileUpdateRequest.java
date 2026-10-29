package phucuong.ht.modules.auth.model.request;

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
 * Tự sửa thông tin cá nhân. Trường null = giữ nguyên. Không có giáo xứ / giáo hạt: đơn vị do quản trị viên điều chuyển.
 * Email trùng và số điện thoại trùng chỉ được kiểm tra khi giá trị thật sự thay đổi.
 */
@Getter
@Setter
public class MyProfileUpdateRequest {

    @Size(max = 50, message = "Tên thánh tối đa 50 ký tự")
    private String holyName;

    @Size(max = 200, message = "Họ và tên tối đa 200 ký tự")
    private String fullName;

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
