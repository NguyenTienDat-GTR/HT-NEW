package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tạo / sửa khóa huấn luyện trong quá khứ: chỉ cần tên khóa và năm tổ chức; không loại khóa, không thời gian đăng ký. */
@Getter
@Setter
public class PastTrainingCourseRequest {

    @NotBlank(message = "Tên khóa huấn luyện là bắt buộc")
    @Size(max = 255, message = "Tên khóa huấn luyện tối đa 255 ký tự")
    private String courseName;

    @NotNull(message = "Năm tổ chức là bắt buộc")
    @Min(value = 1900, message = "Năm tổ chức không hợp lệ")
    @Max(value = 2200, message = "Năm tổ chức không hợp lệ")
    private Integer hostYear;

    // Không bắt buộc: có thể bỏ qua thời gian diễn ra khóa.
    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    private String location;

    // Điểm đạt (mặc định 0 nếu không nhập).
    @DecimalMin(value = "0", message = "Điểm đạt phải lớn hơn hoặc bằng 0")
    private BigDecimal passingScore;

    // Cấp độ đạt được của khóa (danh mục cấp độ của giáo phận). Có cấp độ thì học viên Đạt sẽ có chứng nhận chờ duyệt.
    @Size(max = 50, message = "Cấp độ đạt được tối đa 50 ký tự")
    private String resultingLevel;

    // Hiển thị điểm cho cấp thấp hơn cấp sở hữu khóa (như khóa thường); null = tắt khi tạo, giữ nguyên khi sửa.
    private Boolean scoreVisible;

    private String additionalNotes;
}
