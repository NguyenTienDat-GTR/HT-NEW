package phucuong.ht.modules.leader.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LeaderUpdateRequest {

    @Size(max = 50)
    private String holyName;

    @Email
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải là số điện thoại Việt Nam gồm 10 chữ số và bắt đầu bằng 0")
    @Size(max = 15)
    private String phoneNumber;

    // Id ảnh đại diện mới đã tải lên qua POST /api/media; null = giữ nguyên ảnh hiện tại.
    private UUID imageAssetId;

    // Danh sách trường cần xóa giá trị (ví dụ "imageUrl" để gỡ ảnh đại diện).
    private List<String> clearFields;

    private LeaderGender gender;

    private UUID parishId;
}
