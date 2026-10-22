package phucuong.ht.modules.analytics.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.analytics.model.PeriodRange;
import phucuong.ht.modules.analytics.model.TimeBucket;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricSupportTest {
    @Test void rateReturnsNullWithoutDenominatorInsteadOfFakingZero() {
        assertNull(MetricSupport.rate(3, 0));
        assertEquals(new BigDecimal("66.7"), MetricSupport.rate(2, 3));
        assertEquals(new BigDecimal("100.0"), MetricSupport.rate(5, 5));
    }

    @Test void percentKpiDeltaIsInPercentagePointsAndHasNoRelativeChange() {
        AnalyticsResponse.Kpi kpi = MetricSupport.ratio("passRate", 8, 10, 5L, 10L, null);
        assertEquals(new BigDecimal("80.0"), kpi.getValue());
        assertEquals(new BigDecimal("30.00"), kpi.getDelta());
        assertNull(kpi.getDeltaPct());
    }

    @Test void countKpiHasRelativeChangeOnlyWhenPreviousIsNonZero() {
        assertEquals(new BigDecimal("50.0"), MetricSupport.count("registrations", 15, 10L, null).getDeltaPct());
        assertNull(MetricSupport.count("registrations", 15, 0L, null).getDeltaPct());
        assertNull(MetricSupport.count("registrations", 15, null, null).getDelta());
    }

    @Test void previousPeriodHasSameLengthAndEndsTheDayBeforeCurrent() {
        PeriodRange range = new PeriodRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        PeriodRange previous = range.previous();
        assertEquals(range.days(), previous.days());
        assertEquals(LocalDate.of(2026, 2, 28), previous.to());
    }

    @Test void bucketsAreContinuousAndMatchSqlLabels() {
        PeriodRange range = new PeriodRange(LocalDate.of(2025, 11, 15), LocalDate.of(2026, 2, 3));
        assertEquals(List.of("2025-11", "2025-12", "2026-01", "2026-02"), range.buckets(TimeBucket.MONTH));
        assertEquals(List.of("2025-Q4", "2026-Q1"), range.buckets(TimeBucket.QUARTER));
        assertEquals(List.of("2025", "2026"), range.buckets(TimeBucket.YEAR));
    }

    @Test void funnelStepsAreNestedSoRatesNeverExceedHundred() {
        List<AnalyticsResponse.FunnelStep> steps = MetricSupport.funnel(List.of("a", "b", "c"), 20, 15, 12);
        assertNull(steps.get(0).getRateOfPrevious());
        assertEquals(new BigDecimal("75.0"), steps.get(1).getRateOfPrevious());
        assertEquals(new BigDecimal("80.0"), steps.get(2).getRateOfPrevious());
        assertEquals(new BigDecimal("60.0"), steps.get(2).getRateOfFirst());
    }

    @Test void defaultRangeIsLastTwelveMonthsAndTodayWhenNothingGiven() {
        PeriodRange range = new AnalyticsFilterRequest().resolvedRange(LocalDate.of(2026, 10, 22));
        assertEquals(LocalDate.of(2025, 11, 1), range.from());
        assertEquals(LocalDate.of(2026, 10, 22), range.to());
    }

    @Test void rejectsRangesLongerThanTenYearsAndInvalidGender() {
        AnalyticsFilterRequest wide = new AnalyticsFilterRequest();
        wide.setFromDate(LocalDate.of(2010, 1, 1));
        wide.setToDate(LocalDate.of(2026, 1, 1));
        assertThrows(IllegalArgumentException.class, wide::validate);
        AnalyticsFilterRequest gender = new AnalyticsFilterRequest();
        gender.setGender("X");
        assertThrows(IllegalArgumentException.class, gender::validate);
    }
}
