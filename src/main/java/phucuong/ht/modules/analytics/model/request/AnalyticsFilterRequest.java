package phucuong.ht.modules.analytics.model.request;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import phucuong.ht.modules.analytics.model.PeriodRange;
import phucuong.ht.modules.analytics.model.TimeBucket;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Getter @Setter
public class AnalyticsFilterRequest {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> GROUP_BY_VALUES = Set.of("deanery", "parish", "courseType", "course", "leaderLevel", "gender", "status", "approvalStatus", "year");
    private static final Set<String> GENDERS = Set.of("NAM", "NU");
    private static final long MAX_RANGE_DAYS = 3660;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate;
    private String timeBucket = "month";
    private String groupBy;
    // Mặc định số liệu hiện tại (đơn vị, nhân sự) chỉ tính đơn vị đang hiệu lực; số liệu sự kiện lịch sử luôn đầy đủ.
    private boolean includeInactiveUnits = false;
    // So sánh với kỳ liền trước cùng độ dài.
    private boolean compare = true;
    private UUID deaneryId;
    private UUID parishId;
    private String courseType;
    private String courseCode;
    private String leaderLevel;
    private String gender;
    // Phạm vi cần xem khi tài khoản có nhiều quyền phạm vi: diocese | deanery | parish; rỗng là phạm vi rộng nhất.
    private String scope;

    public TimeBucket resolvedTimeBucket() { return TimeBucket.from(timeBucket); }

    /** Khoảng lọc thực tế: thiếu đầu mút thì mặc định 12 tháng gần nhất; luôn trả về để FE hiển thị đúng kỳ đã tính. */
    public PeriodRange resolvedRange() { return resolvedRange(LocalDate.now(ZONE)); }

    public PeriodRange resolvedRange(LocalDate today) {
        LocalDate to = toDate != null ? toDate : (fromDate != null && fromDate.isAfter(today) ? fromDate : today);
        LocalDate from = fromDate != null ? fromDate : to.minusMonths(11).withDayOfMonth(1);
        return new PeriodRange(from, to);
    }

    public void validate() {
        resolvedTimeBucket();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new IllegalArgumentException("fromDate không được lớn hơn toDate");
        PeriodRange range = resolvedRange();
        if (range.from().isAfter(range.to())) throw new IllegalArgumentException("fromDate không được lớn hơn toDate");
        if (ChronoUnit.DAYS.between(range.from(), range.to()) > MAX_RANGE_DAYS) throw new IllegalArgumentException("Khoảng thời gian tối đa 10 năm");
        if (groupBy != null && !groupBy.isBlank() && !GROUP_BY_VALUES.contains(groupBy)) throw new IllegalArgumentException("groupBy không hợp lệ");
        if (gender != null && !gender.isBlank() && !GENDERS.contains(gender)) throw new IllegalArgumentException("gender chỉ nhận NAM hoặc NU");
        if (scope != null && !scope.isBlank() && !Set.of("diocese", "deanery", "parish").contains(scope.trim().toLowerCase())) throw new IllegalArgumentException("scope chỉ nhận diocese, deanery hoặc parish");
    }

    /** Khóa cache gồm mọi bộ lọc ảnh hưởng tới kết quả. */
    public String cacheKey() {
        return "from=" + fromDate + "|to=" + toDate + "|bucket=" + resolvedTimeBucket() + "|groupBy=" + nz(groupBy)
                + "|inactive=" + includeInactiveUnits + "|compare=" + compare + "|deanery=" + deaneryId + "|parish=" + parishId
                + "|type=" + nz(courseType) + "|course=" + nz(courseCode) + "|level=" + nz(leaderLevel) + "|gender=" + nz(gender) + "|scope=" + nz(scope)
                + "|day=" + LocalDate.now(ZONE);
    }

    private static String nz(String value) { return value == null ? "" : value; }
}
