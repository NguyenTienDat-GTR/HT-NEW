package phucuong.ht.modules.analytics.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Khoảng thời gian đóng [from, to] theo ngày; dùng cho kỳ hiện tại và kỳ liền trước cùng độ dài. */
public record PeriodRange(LocalDate from, LocalDate to) {
    public long days() { return ChronoUnit.DAYS.between(from, to) + 1; }

    public PeriodRange previous() {
        LocalDate previousTo = from.minusDays(1);
        return new PeriodRange(previousTo.minusDays(days() - 1), previousTo);
    }

    /** Nhãn kỳ liên tục (không bỏ trống kỳ không phát sinh), cùng định dạng với biểu thức SQL của repository. */
    public List<String> buckets(TimeBucket bucket) {
        List<String> labels = new ArrayList<>();
        LocalDate cursor = switch (bucket) {
            case MONTH -> from.withDayOfMonth(1);
            case QUARTER -> LocalDate.of(from.getYear(), ((from.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YEAR -> LocalDate.of(from.getYear(), 1, 1);
        };
        while (!cursor.isAfter(to)) {
            labels.add(label(cursor, bucket));
            cursor = switch (bucket) {
                case MONTH -> cursor.plusMonths(1);
                case QUARTER -> cursor.plusMonths(3);
                case YEAR -> cursor.plusYears(1);
            };
        }
        return labels;
    }

    public static String label(LocalDate date, TimeBucket bucket) {
        return switch (bucket) {
            case MONTH -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case QUARTER -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case YEAR -> String.valueOf(date.getYear());
        };
    }
}
