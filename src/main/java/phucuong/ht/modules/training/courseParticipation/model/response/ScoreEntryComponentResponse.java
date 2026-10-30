package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// Thành phần điểm áp dụng cho một khóa, dùng để dựng các cột nhập điểm trực tiếp trên bảng chấm điểm.
@Getter
@Builder
public class ScoreEntryComponentResponse {
    private String id;
    private String name;
    private BigDecimal maxScore;
    private BigDecimal coefficient;
    private Boolean includeInFinal;
    private Integer displayOrder;
}
