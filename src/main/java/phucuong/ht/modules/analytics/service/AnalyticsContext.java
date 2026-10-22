package phucuong.ht.modules.analytics.service;

import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.PeriodRange;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Gom phạm vi quyền + bộ lọc thành các mệnh đề SQL dùng chung để mọi chỉ số cùng một cách chọn dữ liệu.
 * Phạm vi quyền luôn được AND trước, nên bộ lọc giáo hạt/giáo xứ do client gửi không bao giờ mở rộng dữ liệu được xem.
 */
final class AnalyticsContext {
    static final String TZ = "'Asia/Ho_Chi_Minh'";
    static final String UNITS_JOIN = " join parish p on p.id = l.parish_id join deanery d on d.id = p.deanery_id join diocese di on di.id = d.diocese_id ";
    static final String LEVEL_JOIN = " left join leader_level ll on ll.level_code = l.leader_level ";
    static final String LEADER_FROM = " from leader l" + UNITS_JOIN + LEVEL_JOIN;
    static final String PARTICIPATION_FROM = " from course_participation cp join training_course c on c.course_code = cp.course_id"
            + " left join training_course_type ct on ct.type_code = c.course_type_code join leader l on l.id = cp.leader_id" + UNITS_JOIN + LEVEL_JOIN;
    static final String CERTIFICATE_FROM = " from certificate ce join training_course c on c.course_code = ce.course_code"
            + " left join training_course_type ct on ct.type_code = c.course_type_code join leader l on l.id = ce.leader_id" + UNITS_JOIN + LEVEL_JOIN;
    // Các bước phễu lồng nhau (đăng ký ⊇ hợp lệ ⊇ tham dự ⊇ đạt) nên mọi tỷ lệ nằm trong [0, 100].
    static final String PS = "coalesce(cp.participation_status, 'REGISTERED')";
    static final String VALID = PS + " not in ('CANCELLED', 'REJECTED')";
    static final String ATTENDED = PS + " in ('ATTENDED', 'COMPLETED')";
    static final String PASSED = ATTENDED + " and cp.result = 'COMPLETED'";
    static final String CERT_DATE = "coalesce(ce.issue_date, (ce.created_at at time zone " + TZ + ")::date)";
    static final String PARTICIPATION_VALID = "cp.status = true and c.status = true";
    static final String CERTIFICATE_VALID = "ce.status = true and c.status = true";

    final AnalyticsScope scope;
    final AnalyticsFilterRequest filters;
    final PeriodRange range;
    final Map<String, Object> params = new HashMap<>();

    AnalyticsContext(AnalyticsScope scope, AnalyticsFilterRequest filters) {
        this.scope = scope;
        this.filters = filters;
        this.range = filters.resolvedRange();
        if (scope.dioceseId() != null) params.put("dioceseId", scope.dioceseId());
        if (scope.deaneryId() != null) params.put("deaneryId", scope.deaneryId());
        if (scope.parishId() != null) params.put("parishId", scope.parishId());
        if (filters.getDeaneryId() != null) params.put("fDeanery", filters.getDeaneryId());
        if (filters.getParishId() != null) params.put("fParish", filters.getParishId());
        if (has(filters.getLeaderLevel())) params.put("fLevel", filters.getLeaderLevel());
        if (has(filters.getGender())) params.put("fGender", filters.getGender());
        if (has(filters.getCourseType())) params.put("fType", filters.getCourseType());
        if (has(filters.getCourseCode())) params.put("fCourse", filters.getCourseCode());
        bind("cur", range);
        bind("prev", range.previous());
    }

    void bind(String prefix, PeriodRange value) {
        params.put(prefix + "From", value.from());
        params.put(prefix + "To", value.to());
    }

    /** Huynh trưởng thuộc phạm vi quyền + bộ lọc đơn vị/cấp bậc/giới tính (bảng leader tên l, đã join p, d, di). */
    String leaders(boolean snapshot) {
        StringBuilder sql = new StringBuilder(scopeLeaders());
        if (filters.getDeaneryId() != null) sql.append(" and d.id = :fDeanery");
        if (filters.getParishId() != null) sql.append(" and p.id = :fParish");
        if (has(filters.getLeaderLevel())) sql.append(" and l.leader_level = :fLevel");
        if (has(filters.getGender())) sql.append(" and l.gender = :fGender");
        // Số liệu hiện tại bỏ đơn vị đang bị khóa; số liệu sự kiện lịch sử luôn tính đủ.
        if (snapshot && !filters.isIncludeInactiveUnits()) sql.append(" and ").append(operational());
        return sql.toString();
    }

    String scopeLeaders() {
        return switch (scope.type()) {
            case DIOCESE -> "d.diocese_id = :dioceseId";
            case DEANERY -> "p.deanery_id = :deaneryId";
            case PARISH -> "l.parish_id = :parishId";
            case ALL -> "1=1";
        };
    }

    static String operational() { return "p.status = true and d.status = true and di.status = true"; }

    /** Bộ lọc loại khóa/khóa học áp lên bảng training_course tên c. */
    String courseFilters() {
        StringBuilder sql = new StringBuilder();
        if (has(filters.getCourseType())) sql.append(" and c.course_type_code = :fType");
        if (has(filters.getCourseCode())) sql.append(" and c.course_code = :fCourse");
        return sql.toString();
    }

    /** Khóa do chính phạm vi này tổ chức (giáo phận hoặc giáo hạt thuộc phạm vi); giáo xứ không tổ chức khóa. */
    String organizedCourses() {
        return switch (scope.type()) {
            case DIOCESE -> "(c.diocese_id = :dioceseId or c.deanery_id in (select de.id from deanery de where de.diocese_id = :dioceseId))";
            case DEANERY -> "c.deanery_id = :deaneryId";
            default -> "1=0";
        };
    }

    boolean hasLeaderDimensionFilter() {
        return filters.getDeaneryId() != null || filters.getParishId() != null || has(filters.getLeaderLevel()) || has(filters.getGender());
    }

    /** Hồ sơ đăng ký thuộc các khóa bắt đầu trong kỳ prefix (cur | prev): cohort theo ngày bắt đầu khóa. */
    String participations(String prefix) {
        return leaders(false) + " and " + PARTICIPATION_VALID + courseFilters()
                + " and c.start_date between :" + prefix + "From and :" + prefix + "To";
    }

    String certificates(String prefix) {
        return leaders(false) + " and " + CERTIFICATE_VALID + courseFilters()
                + " and " + CERT_DATE + " between :" + prefix + "From and :" + prefix + "To";
    }

    static boolean has(String value) { return value != null && !value.isBlank(); }
}
