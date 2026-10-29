package phucuong.ht.modules.analytics.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Chỉ dữ liệu cấu trúc: FE tự ánh xạ key sang nhãn hiển thị; `label` chỉ là tên thực thể lấy từ dữ liệu (giáo hạt, khóa huấn luyện...). */
@Getter @Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsResponse {
    private final String scope;
    private final Filters filters;
    private final List<Kpi> kpis;
    private final List<Breakdown> breakdowns;
    private final List<Trend> trends;
    private final List<Trend> previousTrends;
    private final List<FunnelStep> funnel;
    private final List<Alert> alerts;

    @Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Filters {
        private final LocalDate fromDate;
        private final LocalDate toDate;
        private final LocalDate compareFromDate;
        private final LocalDate compareToDate;
        private final String timeBucket;
        private final String groupBy;
        private final boolean includeInactiveUnits;
    }

    /** unit: COUNT | PERCENT | SCORE | HOURS. delta là chênh lệch tuyệt đối (điểm phần trăm với PERCENT), deltaPct là % thay đổi (chỉ với COUNT/SCORE/HOURS). */
    @Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Kpi {
        private final String key;
        private final String unit;
        private final BigDecimal value;
        private final BigDecimal previous;
        private final BigDecimal delta;
        private final BigDecimal deltaPct;
        private final Long numerator;
        private final Long denominator;
        private final List<Point> sparkline;
    }

    @Getter @Builder public static class Point { private final String period; private final BigDecimal value; }
    @Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Breakdown { private final String type; private final String key; private final String label; private final Map<String, Object> metrics; }
    @Getter @Builder public static class Trend { private final String period; private final Map<String, Object> metrics; }
    /** rateOfPrevious: so với bước liền trước; rateOfFirst: so với bước đầu tiên; luôn trong [0, 100] vì các bước lồng nhau. */
    @Getter @Builder public static class FunnelStep { private final String key; private final long count; private final BigDecimal rateOfPrevious; private final BigDecimal rateOfFirst; }
    /** severity: critical | warning | info. items là các đối tượng cần chú ý (tối đa 5), value là chỉ số gây cảnh báo. */
    @Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Alert { private final String key; private final String severity; private final long count; private final String href; private final List<AlertItem> items; }
    @Getter @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AlertItem { private final String id; private final String label; private final BigDecimal value; }
}
