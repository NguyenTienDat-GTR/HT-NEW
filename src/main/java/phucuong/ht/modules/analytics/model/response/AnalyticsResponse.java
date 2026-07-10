package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter @Builder
public class AnalyticsResponse {
    private final String scope;
    private final Filters filters;
    private final Map<String, Object> kpis;
    private final List<Breakdown> breakdowns;
    private final List<Trend> trends;
    private final List<Comparison> comparisons;
    @Getter @Builder public static class Filters { private final LocalDate fromDate; private final LocalDate toDate; private final String timeBucket; private final String groupBy; }
    @Getter @Builder public static class Breakdown { private final String type; private final String key; private final Map<String, Object> metrics; }
    @Getter @Builder public static class Trend { private final String period; private final Map<String, Object> metrics; }
    @Getter @Builder public static class Comparison { private final String period; private final Map<String, Object> metrics; }
}
