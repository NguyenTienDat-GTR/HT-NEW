package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.courseParticipation.service.ScoreCalculator.ScoredComponent;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedScoreCalculatorTest {

    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();

    private static ScoredComponent component(String score, String coefficient, boolean included) {
        return new ScoredComponent(new BigDecimal(score), new BigDecimal(coefficient), included);
    }

    @Test
    void averagesByCoefficientAndRoundsHalfUp() {
        // (8 * 1 + 7 * 2) / 3 = 7.333... -> 7.33
        BigDecimal total = calculator.total(List.of(component("8", "1", true), component("7", "2", true))).orElseThrow();

        assertEquals(new BigDecimal("7.33"), total);
    }

    @Test
    void roundsHalfUpAtTheMidpoint() {
        // (7.12 + 7.13) / 2 = 7.125 đúng điểm giữa -> làm tròn lên 7.13
        BigDecimal total = calculator.total(List.of(component("7.12", "1", true), component("7.13", "1", true))).orElseThrow();

        assertEquals(new BigDecimal("7.13"), total);
    }

    @Test
    void ignoresComponentsNotIncludedInFinal() {
        BigDecimal total = calculator.total(List.of(component("10", "1", false), component("6", "1", true))).orElseThrow();

        assertEquals(new BigDecimal("6.00"), total);
    }

    @Test
    void returnsEmptyWhenNothingCountsTowardFinal() {
        assertTrue(calculator.total(List.of(component("10", "1", false))).isEmpty());
        assertTrue(calculator.total(List.of()).isEmpty());
        assertTrue(calculator.total(List.of(component("10", "0", true))).isEmpty());
    }

    @Test
    void passingMeansNotBelowPassingScore() {
        assertTrue(calculator.isPassing(new BigDecimal("5.00"), new BigDecimal("5")));
        assertTrue(calculator.isPassing(new BigDecimal("5.01"), new BigDecimal("5")));
        assertFalse(calculator.isPassing(new BigDecimal("4.99"), new BigDecimal("5")));
    }
}
