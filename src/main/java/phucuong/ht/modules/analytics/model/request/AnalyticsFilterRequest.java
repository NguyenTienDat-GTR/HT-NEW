package phucuong.ht.modules.analytics.model.request;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import phucuong.ht.modules.analytics.model.TimeBucket;

import java.time.LocalDate;
import java.util.Set;

@Getter @Setter
public class AnalyticsFilterRequest {
    private static final Set<String> GROUP_BY_VALUES = Set.of("deanery", "parish", "courseType", "leaderLevel", "gender", "status", "approvalStatus", "year");
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate;
    private String timeBucket = "month";
    private String groupBy;
    public TimeBucket resolvedTimeBucket() { return TimeBucket.from(timeBucket); }
    public void validate() {
        resolvedTimeBucket();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new IllegalArgumentException("fromDate không được lớn hơn toDate");
        if (groupBy != null && !groupBy.isBlank() && !GROUP_BY_VALUES.contains(groupBy)) throw new IllegalArgumentException("groupBy không hợp lệ");
    }
}
