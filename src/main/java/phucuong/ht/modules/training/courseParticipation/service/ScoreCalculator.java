package phucuong.ht.modules.training.courseParticipation.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Chiến lược tính điểm tổng kết của một hồ sơ tham dự. Tách khỏi {@link CourseParticipationService}
 * để đổi công thức (vd. thang điểm khác) mà không đụng vào luồng lưu điểm.
 */
public interface ScoreCalculator {

    /** Điểm một thành phần của hồ sơ cùng hệ số và cờ có tính vào điểm tổng kết hay không. */
    record ScoredComponent(BigDecimal score, BigDecimal coefficient, boolean includedInFinal) {
    }

    /** Điểm tổng kết; rỗng nếu không có thành phần nào được tính vào điểm tổng kết. */
    Optional<BigDecimal> total(List<ScoredComponent> components);

    /** Đạt khi điểm tổng kết không thấp hơn điểm đạt của khóa. */
    boolean isPassing(BigDecimal total, BigDecimal passingScore);
}
