package phucuong.ht.modules.training.courseScoreComponent.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Một thành phần điểm khai báo trong khóa. Có id = sửa thành phần đã có của khóa; không có id = thêm mới.
// Thứ tự trong danh sách gửi lên là thứ tự hiển thị.
@Getter
@Setter
public class ScoreComponentInput {

    private String id;

    @NotBlank(message = "Tên thành phần điểm là bắt buộc")
    @Size(max = 255, message = "Tên thành phần điểm tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả thành phần điểm tối đa 1000 ký tự")
    private String description;

    @NotNull(message = "Điểm tối đa là bắt buộc")
    @DecimalMin(value = "0.01", message = "Điểm tối đa phải lớn hơn 0")
    private BigDecimal maxScore;

    @NotNull(message = "Hệ số là bắt buộc")
    @DecimalMin(value = "0.01", message = "Hệ số phải lớn hơn 0")
    private BigDecimal coefficient;

    private Boolean includeInFinal;
}
