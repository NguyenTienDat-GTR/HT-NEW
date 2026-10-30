package phucuong.ht.modules.training.courseScoreComponent.model.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// Thành phần điểm của một khóa.
@Getter
@Builder
public class CourseScoreComponentResponse {
    // Id thành phần của khóa; gửi lại khi sửa khóa để cập nhật đúng thành phần.
    private String id;
    private String name;
    private String description;
    private BigDecimal maxScore;
    private BigDecimal coefficient;
    private Boolean includeInFinal;
    private Integer displayOrder;
    // Đã có điểm được nhập cho thành phần này trong khóa.
    private Boolean hasScores;
}
