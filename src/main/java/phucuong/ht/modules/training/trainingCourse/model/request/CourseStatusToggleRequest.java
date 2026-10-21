package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Vô hiệu hóa / bật lại khóa huấn luyện. targetStatus là trạng thái muốn đạt được (true = bật, false = vô hiệu hóa);
// BE kiểm tra khớp với trạng thái hiện tại để tránh đổi nhầm khi dữ liệu đã thay đổi.
@Getter
@Setter
public class CourseStatusToggleRequest {

    @NotBlank(message = "Xác nhận là bắt buộc")
    private String confirmationText;

    @NotNull(message = "Trạng thái đích là bắt buộc")
    private Boolean targetStatus;
}
