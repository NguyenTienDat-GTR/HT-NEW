package phucuong.ht.common.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.Test;
import phucuong.ht.common.exception.ApiException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class YearFilterPredicateTest {

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Predicate predicate = mock(Predicate.class);

    private Path<?> pathOf(Class<?> type) {
        Path path = mock(Path.class);
        when(path.getJavaType()).thenReturn((Class) type);
        return path;
    }

    @Test
    void localDateYearBecomesFullYearRange() {
        Path<?> path = pathOf(LocalDate.class);
        when(cb.between(any(Expression.class), any(Comparable.class), any(Comparable.class))).thenReturn(predicate);

        Predicate result = YearFilterPredicate.build(cb, path, "2026");

        assertSame(predicate, result);
        verify(cb).between((Expression) path, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    @Test
    void severalYearsAreCombinedWithOr() {
        Path<?> path = pathOf(LocalDate.class);
        Predicate first = mock(Predicate.class);
        Predicate second = mock(Predicate.class);
        Predicate any = mock(Predicate.class);
        when(cb.between((Expression) path, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))).thenReturn(first);
        when(cb.between((Expression) path, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).thenReturn(second);
        when(cb.or(any(Predicate[].class))).thenReturn(any);

        Predicate result = YearFilterPredicate.build(cb, path, List.of(2025, "2026"));

        assertSame(any, result);
        verify(cb).or(new Predicate[]{first, second});
    }

    @Test
    void offsetDateTimeYearUsesVietnamMidnightBounds() {
        Path<?> path = pathOf(OffsetDateTime.class);
        Predicate lower = mock(Predicate.class);
        Predicate upper = mock(Predicate.class);
        ZoneOffset zone = ZoneOffset.ofHours(7);
        when(cb.greaterThanOrEqualTo((Expression) path, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone))).thenReturn(lower);
        when(cb.lessThan((Expression) path, OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone))).thenReturn(upper);
        when(cb.and(lower, upper)).thenReturn(predicate);

        assertSame(predicate, YearFilterPredicate.build(cb, path, 2026));
    }

    @Test
    void invalidYearOrColumnTypeIsRejectedAsBadRequest() {
        assertThrows(ApiException.class, () -> YearFilterPredicate.build(cb, pathOf(LocalDate.class), "abc"));
        assertThrows(ApiException.class, () -> YearFilterPredicate.build(cb, pathOf(LocalDate.class), "0"));
        assertThrows(ApiException.class, () -> YearFilterPredicate.build(cb, pathOf(LocalDate.class), List.of()));
        assertThrows(ApiException.class, () -> YearFilterPredicate.build(cb, pathOf(String.class), "2026"));
    }
}
