package phucuong.ht.modules.analytics.service;

import phucuong.ht.modules.analytics.model.PeriodRange;
import phucuong.ht.modules.analytics.model.TimeBucket;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Phép tính dùng chung: tỷ lệ, chênh lệch kỳ trước, chuỗi thời gian liên tục. Mọi tỷ lệ chia cho mẫu số cùng cohort. */
final class MetricSupport {
    static final String COUNT = "COUNT";
    static final String PERCENT = "PERCENT";
    static final String SCORE = "SCORE";
    static final String HOURS = "HOURS";

    private MetricSupport() { }

    static long num(Object value) { return value == null ? 0L : ((Number) value).longValue(); }

    static BigDecimal dec(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Phần trăm làm tròn 1 chữ số; mẫu số 0 trả null (không có dữ liệu) thay vì giả 0%. */
    static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) return null;
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(1, RoundingMode.HALF_UP);
    }

    static BigDecimal rateOrZero(long numerator, long denominator) {
        BigDecimal value = rate(numerator, denominator);
        return value == null ? BigDecimal.ZERO.setScale(1) : value;
    }

    static AnalyticsResponse.Kpi count(String key, long value, Long previous, List<AnalyticsResponse.Point> sparkline) {
        return kpi(key, COUNT, BigDecimal.valueOf(value), previous == null ? null : BigDecimal.valueOf(previous), null, null, sparkline);
    }

    static AnalyticsResponse.Kpi ratio(String key, long numerator, long denominator, Long previousNumerator, Long previousDenominator, List<AnalyticsResponse.Point> sparkline) {
        BigDecimal previous = previousNumerator == null ? null : rate(previousNumerator, previousDenominator);
        return kpi(key, PERCENT, rate(numerator, denominator), previous, numerator, denominator, sparkline);
    }

    static AnalyticsResponse.Kpi measure(String key, String unit, BigDecimal value, BigDecimal previous) {
        return kpi(key, unit, value, previous, null, null, null);
    }

    /** delta tuyệt đối (điểm phần trăm với tỷ lệ); deltaPct chỉ có khi kỳ trước khác 0 và không phải tỷ lệ. */
    private static AnalyticsResponse.Kpi kpi(String key, String unit, BigDecimal value, BigDecimal previous, Long numerator, Long denominator, List<AnalyticsResponse.Point> sparkline) {
        BigDecimal delta = value == null || previous == null ? null : value.subtract(previous).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaPct = null;
        if (delta != null && !PERCENT.equals(unit) && previous.signum() != 0)
            deltaPct = delta.multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1, RoundingMode.HALF_UP);
        return AnalyticsResponse.Kpi.builder().key(key).unit(unit).value(value).previous(previous).delta(delta).deltaPct(deltaPct)
                .numerator(numerator).denominator(denominator).sparkline(sparkline).build();
    }

    static List<AnalyticsResponse.Point> sparkline(List<AnalyticsResponse.Trend> trends, String metric) {
        List<AnalyticsResponse.Point> points = new ArrayList<>();
        for (AnalyticsResponse.Trend trend : trends) {
            Object value = trend.getMetrics().get(metric);
            points.add(AnalyticsResponse.Point.builder().period(trend.getPeriod()).value(value == null ? null : new BigDecimal(value.toString())).build());
        }
        return points;
    }

    /** Tỷ lệ theo từng kỳ từ hai chuỗi đếm; kỳ không có mẫu số là null để biểu đồ ngắt đường thay vì rơi về 0. */
    static List<AnalyticsResponse.Point> ratioSparkline(List<AnalyticsResponse.Trend> trends, String numerator, String denominator) {
        List<AnalyticsResponse.Point> points = new ArrayList<>();
        for (AnalyticsResponse.Trend trend : trends) {
            points.add(AnalyticsResponse.Point.builder().period(trend.getPeriod())
                    .value(rate(num(trend.getMetrics().get(numerator)), num(trend.getMetrics().get(denominator)))).build());
        }
        return points;
    }

    static String bucketFormat(TimeBucket bucket) {
        return switch (bucket) {
            case MONTH -> "YYYY-MM";
            case QUARTER -> "YYYY-\"Q\"Q";
            case YEAR -> "YYYY";
        };
    }

    /**
     * Chuỗi thời gian liên tục: mỗi kỳ trong khoảng đều có điểm (0 nếu không phát sinh).
     * aggregates: các biểu thức count(...) theo thứ tự names; dateExpr: biểu thức ngày dùng để chia kỳ.
     */
    static List<AnalyticsResponse.Trend> trend(AnalyticsRepository repository, Map<String, Object> params, String from, String where,
                                                String dateExpr, TimeBucket bucket, PeriodRange range, List<String> names, List<String> aggregates) {
        String period = "to_char(" + dateExpr + ", '" + bucketFormat(bucket) + "')";
        String sql = "select " + period + ", " + String.join(", ", aggregates) + from + " where " + where + " group by 1";
        Map<String, Object[]> byPeriod = new HashMap<>();
        for (Object[] row : repository.rows(sql, params)) byPeriod.put(String.valueOf(row[0]), row);
        List<AnalyticsResponse.Trend> result = new ArrayList<>();
        for (String label : range.buckets(bucket)) {
            Object[] row = byPeriod.get(label);
            Map<String, Object> metrics = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) metrics.put(names.get(i), row == null ? 0L : num(row[i + 1]));
            result.add(AnalyticsResponse.Trend.builder().period(label).metrics(metrics).build());
        }
        return result;
    }

    static AnalyticsResponse.Breakdown breakdown(String type, String key, String label, Map<String, Object> metrics) {
        return AnalyticsResponse.Breakdown.builder().type(type).key(key).label(label).metrics(metrics).build();
    }

    static AnalyticsResponse.Alert alert(String key, String severity, long count, String href, List<AnalyticsResponse.AlertItem> items) {
        return AnalyticsResponse.Alert.builder().key(key).severity(severity).count(count).href(href).items(items).build();
    }

    static AnalyticsResponse.AlertItem item(Object id, String label, BigDecimal value) {
        return AnalyticsResponse.AlertItem.builder().id(id == null ? null : id.toString()).label(label).value(value).build();
    }

    /** Phễu từ các số đếm lồng nhau theo thứ tự; tỷ lệ bước dùng mẫu số là bước liền trước. */
    static List<AnalyticsResponse.FunnelStep> funnel(List<String> keys, long... counts) {
        List<AnalyticsResponse.FunnelStep> steps = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            steps.add(AnalyticsResponse.FunnelStep.builder().key(keys.get(i)).count(counts[i])
                    .rateOfPrevious(i == 0 ? null : rate(counts[i], counts[i - 1]))
                    .rateOfFirst(i == 0 ? null : rate(counts[i], counts[0])).build());
        }
        return steps;
    }
}
