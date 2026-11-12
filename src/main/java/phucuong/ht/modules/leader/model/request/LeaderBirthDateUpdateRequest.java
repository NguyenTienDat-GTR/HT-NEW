package phucuong.ht.modules.leader.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Sửa riêng ngày sinh của huynh trưởng (quyền organization.leader.update_birth_date.*), tách khỏi sửa hồ sơ.
 * Tên thuộc tính trùng tên thuộc tính của Leader để BaseRepo sao chép tự động.
 */
@Getter
@Setter
public class LeaderBirthDateUpdateRequest {

    @NotNull(message = "Vui lòng nhập ngày sinh")
    @PastOrPresent(message = "Ngày sinh không được sau ngày hiện tại")
    private LocalDate birthDate;
}
