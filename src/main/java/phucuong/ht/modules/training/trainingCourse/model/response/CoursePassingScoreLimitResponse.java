package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// Điểm đạt tối đa có thể đặt cho một loại khóa (null khi loại khóa chưa có thành phần tính vào điểm tổng kết).
@Getter
@Builder
public class CoursePassingScoreLimitResponse {
    private BigDecimal maxPassingScore;
    private Integer componentCount;
}
