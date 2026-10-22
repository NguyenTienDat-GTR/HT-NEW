package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter @Builder
public class AnalyticsRecordsResponse {
    private final String dataset;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final long total;
    private final int page;
    private final int size;
}
