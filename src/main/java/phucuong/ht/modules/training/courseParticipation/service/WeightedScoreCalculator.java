package phucuong.ht.modules.training.courseParticipation.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Điểm tổng kết = trung bình có hệ số của các thành phần có includedInFinal = true, làm tròn 2 chữ số (HALF_UP). */
@Component
public class WeightedScoreCalculator implements ScoreCalculator {

    @Override
    public Optional<BigDecimal> total(List<ScoredComponent> components) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal coefficientSum = BigDecimal.ZERO;
        for (ScoredComponent component : components) {
            if (component.includedInFinal()) {
                weightedSum = weightedSum.add(component.score().multiply(component.coefficient()));
                coefficientSum = coefficientSum.add(component.coefficient());
            }
        }
        if (coefficientSum.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(weightedSum.divide(coefficientSum, 2, RoundingMode.HALF_UP));
    }

    @Override
    public boolean isPassing(BigDecimal total, BigDecimal passingScore) {
        return total.compareTo(passingScore) >= 0;
    }
}
