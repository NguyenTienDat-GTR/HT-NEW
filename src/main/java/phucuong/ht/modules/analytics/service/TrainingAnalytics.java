package phucuong.ht.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static phucuong.ht.modules.analytics.service.AnalyticsContext.*;
import static phucuong.ht.modules.analytics.service.MetricSupport.*;

/**
 * Phân tích khóa huấn luyện và chứng nhận.
 * Khóa huấn luyện: cohort là các khóa có start_date trong kỳ; mọi bước phễu đếm trên cùng tập hồ sơ nên tỷ lệ không vượt 100%.
 * Chứng nhận: mốc ngày là ngày cấp, hoặc ngày tạo khi chưa cấp, để hồ sơ chờ duyệt không bị bỏ sót.
 */
@Component @RequiredArgsConstructor
class TrainingAnalytics {
    static final long MIN_SAMPLE = 5;
    static final int LOW_PASS_RATE = 50;
    static final int HIGH_CANCEL_RATE = 20;
    static final int HIGH_REJECT_RATE = 20;
    static final int SLOW_APPROVAL_DAYS = 7;
    private static final int TOP_LIMIT = 15;
    private static final int COURSE_ROWS_LIMIT = 200;
    static final List<String> FUNNEL_KEYS = List.of("registered", "valid", "attended", "passed", "certificate");

    private final AnalyticsRepository repository;

    record CourseMetrics(long registrations, long valid, long cancelled, long rejected, long attended, long passed, long certificateApproved,
                         long pendingApproval, long trainedLeaders, long courses, BigDecimal averageScore) { }

    CourseMetrics courseMetrics(AnalyticsContext ctx, String prefix) {
        String sql = "select count(*), count(*) filter (where " + VALID + "), count(*) filter (where " + PS + " = 'CANCELLED'), count(*) filter (where " + PS + " = 'REJECTED'),"
                + " count(*) filter (where " + ATTENDED + "), count(*) filter (where " + PASSED + "),"
                + " count(*) filter (where " + PASSED + " and cp.certificate_approval_status = 'APPROVED'),"
                + " count(*) filter (where cp.approval_status in ('PENDING', 'SUBMITTED')),"
                + " count(distinct cp.leader_id) filter (where " + PASSED + "), count(distinct c.course_code),"
                + " avg(cp.total_score) filter (where " + ATTENDED + " and cp.total_score is not null)"
                + PARTICIPATION_FROM + " where " + ctx.participations(prefix);
        Object[] r = repository.row(sql, ctx.params);
        return new CourseMetrics(num(r[0]), num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7]), num(r[8]), num(r[9]), dec(r[10]));
    }

    List<AnalyticsResponse.Trend> courseTrend(AnalyticsContext ctx, String prefix, phucuong.ht.modules.analytics.model.PeriodRange range) {
        return trend(repository, ctx.params, PARTICIPATION_FROM, ctx.participations(prefix), "c.start_date", ctx.filters.resolvedTimeBucket(), range,
                List.of("registrations", "attended", "passed"),
                List.of("count(*)", "count(*) filter (where " + ATTENDED + ")", "count(*) filter (where " + PASSED + ")"));
    }

    List<AnalyticsResponse.FunnelStep> courseFunnel(CourseMetrics m) {
        return funnel(FUNNEL_KEYS, m.registrations(), m.valid(), m.attended(), m.passed(), m.certificateApproved());
    }

    // ---------------------------------------------------------------- courses

    AnalyticsResponse courses(AnalyticsContext ctx) {
        if (ctx.scope.type() == AnalyticsScopeType.PARISH) throw new phucuong.ht.modules.analytics.security.AnalyticsAccessDeniedException("Phạm vi giáo xứ không xem phân tích khóa huấn luyện");
        CourseMetrics cur = courseMetrics(ctx, "cur");
        CourseMetrics prev = ctx.filters.isCompare() ? courseMetrics(ctx, "prev") : null;
        List<AnalyticsResponse.Trend> trends = courseTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> previousTrends = prev == null ? null : courseTrend(ctx, "prev", ctx.range.previous());
        List<AnalyticsResponse.Breakdown> courseRows = courseRows(ctx, "cur");
        Long prevCourses = prev == null ? null : (long) courseRows(ctx, "prev").size();

        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        kpis.add(count("totalCourses", courseRows.size(), prevCourses, null));
        kpis.add(count("registrations", cur.registrations(), prev == null ? null : prev.registrations(), sparkline(trends, "registrations")));
        kpis.add(ratio("attendanceRate", cur.attended(), cur.valid(), prev == null ? null : prev.attended(), prev == null ? null : prev.valid(), null));
        kpis.add(ratio("passRate", cur.passed(), cur.attended(), prev == null ? null : prev.passed(), prev == null ? null : prev.attended(), ratioSparkline(trends, "passed", "attended")));
        kpis.add(ratio("cancellationRate", cur.cancelled(), cur.registrations(), prev == null ? null : prev.cancelled(), prev == null ? null : prev.registrations(), null));
        // Hồ sơ bị từ chối duyệt (gồm hồ sơ bị xóa khỏi danh sách tham dự) vẫn còn trong hệ thống và có thể được duyệt lại.
        kpis.add(ratio("rejectionRate", cur.rejected(), cur.registrations(), prev == null ? null : prev.rejected(), prev == null ? null : prev.registrations(), null));
        kpis.add(ratio("certificateRate", cur.certificateApproved(), cur.passed(), prev == null ? null : prev.certificateApproved(), prev == null ? null : prev.passed(), null));
        kpis.add(measure("averageScore", SCORE, cur.averageScore(), prev == null ? null : prev.averageScore()));
        kpis.add(count("pendingApproval", cur.pendingApproval(), prev == null ? null : prev.pendingApproval(), null));
        kpis.add(count("rejectedRegistrations", cur.rejected(), prev == null ? null : prev.rejected(), null));
        kpis.add(count("trainedLeaders", cur.trainedLeaders(), prev == null ? null : prev.trainedLeaders(), null));

        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>(courseRows);
        for (String dimension : defaultCourseDimensions(ctx)) breakdowns.addAll(participationGroup(ctx, dimension, dimension));
        String groupBy = ctx.filters.getGroupBy();
        if (AnalyticsContext.has(groupBy) && participationDimension(groupBy) != null) breakdowns.addAll(participationGroup(ctx, groupBy, "grouped"));
        breakdowns.addAll(scoreBuckets(ctx));
        breakdowns.addAll(participationStatuses(ctx));
        breakdowns.addAll(heat(ctx, PARTICIPATION_FROM, ctx.participations("cur"), "c.start_date", "count(*)"));
        return AnalyticsResponse.builder().kpis(kpis).breakdowns(breakdowns).trends(trends).previousTrends(previousTrends)
                .funnel(courseFunnel(cur)).alerts(courseAlerts(ctx, courseRows)).build();
    }

    private List<String> defaultCourseDimensions(AnalyticsContext ctx) {
        List<String> dimensions = new ArrayList<>(List.of("courseType", "leaderLevel", "gender"));
        if (ctx.scope.type() == AnalyticsScopeType.DIOCESE) dimensions.add(0, "deanery");
        if (ctx.scope.type() == AnalyticsScopeType.DEANERY) dimensions.add(0, "parish");
        return dimensions;
    }

    /** Mỗi dòng là một khóa: gồm cả khóa chưa có hồ sơ nào khi không lọc theo đối tượng tham gia. */
    private List<AnalyticsResponse.Breakdown> courseRows(AnalyticsContext ctx, String prefix) {
        String participants = "select l.id" + LEADER_FROM + " where " + ctx.leaders(false);
        String hasParticipants = "c.course_code in (select x.course_id from course_participation x where x.status = true and x.leader_id in (" + participants + "))";
        String organized = ctx.organizedCourses();
        String visible = ctx.hasLeaderDimensionFilter() ? hasParticipants : "(" + organized + " or " + hasParticipants + ")";
        String sql = "select c.course_code, c.course_name, c.course_type_code, ct.type_name, c.start_date, c.end_date, c.organizing_status,"
                + " count(cp.id), count(cp.id) filter (where " + VALID + "), count(cp.id) filter (where " + PS + " = 'CANCELLED'),"
                + " count(cp.id) filter (where " + ATTENDED + "), count(cp.id) filter (where " + PASSED + "),"
                + " count(cp.id) filter (where " + PASSED + " and cp.certificate_approval_status = 'APPROVED'),"
                + " avg(cp.total_score) filter (where " + ATTENDED + " and cp.total_score is not null),"
                + " count(cp.id) filter (where " + PS + " = 'REJECTED')"
                + " from training_course c left join training_course_type ct on ct.type_code = c.course_type_code"
                + " left join course_participation cp on cp.course_id = c.course_code and cp.status = true and cp.leader_id in (" + participants + ")"
                + " where c.status = true and c.start_date between :" + prefix + "From and :" + prefix + "To" + ctx.courseFilters() + " and " + visible
                + " group by c.course_code, c.course_name, c.course_type_code, ct.type_name, c.start_date, c.end_date, c.organizing_status"
                + " order by c.start_date desc, c.course_name";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            if (result.size() >= COURSE_ROWS_LIMIT) break;
            Map<String, Object> metrics = new LinkedHashMap<>();
            long attended = num(r[10]);
            metrics.put("courseType", r[2]);
            metrics.put("courseTypeLabel", r[3]);
            metrics.put("startDate", String.valueOf(r[4]));
            metrics.put("endDate", r[5] == null ? null : String.valueOf(r[5]));
            metrics.put("organizingStatus", r[6]);
            metrics.put("registrations", num(r[7]));
            metrics.put("valid", num(r[8]));
            metrics.put("cancelled", num(r[9]));
            metrics.put("attended", attended);
            metrics.put("passed", num(r[11]));
            metrics.put("certificateApproved", num(r[12]));
            metrics.put("passRate", rate(num(r[11]), attended));
            metrics.put("averageScore", dec(r[13]));
            metrics.put("rejected", num(r[14]));
            result.add(breakdown("course", String.valueOf(r[0]), String.valueOf(r[1]), metrics));
        }
        return result;
    }

    private static String[] participationDimension(String dimension) {
        return switch (dimension) {
            case "deanery" -> new String[]{"d.id::text", "d.name"};
            case "parish" -> new String[]{"p.id::text", "p.name"};
            case "courseType" -> new String[]{"coalesce(c.course_type_code, '-')", "ct.type_name"};
            case "course" -> new String[]{"c.course_code", "c.course_name"};
            case "leaderLevel" -> new String[]{"l.leader_level", "ll.level_name"};
            case "gender" -> new String[]{"coalesce(l.gender, 'UNKNOWN')", "null::text"};
            case "year" -> new String[]{"extract(year from c.start_date)::int::text", "null::text"};
            default -> null;
        };
    }

    List<AnalyticsResponse.Breakdown> participationGroup(AnalyticsContext ctx, String dimension, String type) {
        String[] expression = participationDimension(dimension);
        String sql = "select " + expression[0] + ", max(" + expression[1] + "), count(*), count(*) filter (where " + VALID + "),"
                + " count(*) filter (where " + ATTENDED + "), count(*) filter (where " + PASSED + ")"
                + PARTICIPATION_FROM + " where " + ctx.participations("cur") + " group by 1 order by count(*) desc, 1 limit " + TOP_LIMIT;
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("dimension", dimension);
            metrics.put("registrations", num(r[2]));
            metrics.put("valid", num(r[3]));
            metrics.put("attended", num(r[4]));
            metrics.put("passed", num(r[5]));
            metrics.put("passRate", rate(num(r[5]), num(r[4])));
            result.add(breakdown(type, String.valueOf(r[0]), r[1] == null ? null : String.valueOf(r[1]), metrics));
        }
        return result;
    }

    /** Cảnh báo trong kỳ đã chọn; ngưỡng và mẫu tối thiểu là hằng số để tránh báo động từ mẫu quá nhỏ. */
    private List<AnalyticsResponse.Alert> courseAlerts(AnalyticsContext ctx, List<AnalyticsResponse.Breakdown> courseRows) {
        List<AnalyticsResponse.AlertItem> lowPass = new ArrayList<>();
        List<AnalyticsResponse.AlertItem> highCancel = new ArrayList<>();
        long lowPassCount = 0;
        long highCancelCount = 0;
        for (AnalyticsResponse.Breakdown row : courseRows) {
            Map<String, Object> m = row.getMetrics();
            long attended = num(m.get("attended"));
            long registrations = num(m.get("registrations"));
            BigDecimal passRate = (BigDecimal) m.get("passRate");
            if ("ENDED".equals(m.get("organizingStatus")) && attended >= MIN_SAMPLE && passRate != null && passRate.intValue() < LOW_PASS_RATE) {
                lowPassCount++;
                if (lowPass.size() < 5) lowPass.add(item(row.getKey(), row.getLabel(), passRate));
            }
            BigDecimal cancelRate = rate(num(m.get("cancelled")), registrations);
            if (registrations >= MIN_SAMPLE && cancelRate != null && cancelRate.intValue() > HIGH_CANCEL_RATE) {
                highCancelCount++;
                if (highCancel.size() < 5) highCancel.add(item(row.getKey(), row.getLabel(), cancelRate));
            }
        }
        List<AnalyticsResponse.Alert> alerts = new ArrayList<>();
        if (lowPassCount > 0) alerts.add(alert("lowPassRateCourses", "warning", lowPassCount, "/training/courses", lowPass));
        if (highCancelCount > 0) alerts.add(alert("highCancellationCourses", "warning", highCancelCount, "/training/courses", highCancel));
        return alerts;
    }

    /** Phân bố điểm tổng kết (thang 10) của hồ sơ đã tham dự: mỗi ô một điểm nguyên, điểm 10 gộp vào ô cuối; ô trống vẫn có mặt để biểu đồ liên tục. */
    private List<AnalyticsResponse.Breakdown> scoreBuckets(AnalyticsContext ctx) {
        String sql = "select least(floor(cp.total_score), 10)::int, count(*)" + PARTICIPATION_FROM + " where " + ctx.participations("cur")
                + " and " + ATTENDED + " and cp.total_score is not null group by 1";
        long[] counts = new long[11];
        for (Object[] r : repository.rows(sql, ctx.params)) {
            int bucket = (int) num(r[0]);
            if (bucket >= 0 && bucket <= 10) counts[bucket] = num(r[1]);
        }
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (int i = 0; i <= 10; i++) result.add(breakdown("scoreBucket", String.valueOf(i), null, Map.of("count", counts[i])));
        return result;
    }

    private List<AnalyticsResponse.Breakdown> participationStatuses(AnalyticsContext ctx) {
        String sql = "select " + PS + ", count(*)" + PARTICIPATION_FROM + " where " + ctx.participations("cur") + " group by 1 order by count(*) desc";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) result.add(breakdown("participationStatus", String.valueOf(r[0]), null, Map.of("count", num(r[1]))));
        return result;
    }

    /** Thời gian từ lúc tạo đến lúc duyệt của chứng nhận đã duyệt, chia theo ngưỡng cố định. */
    private List<AnalyticsResponse.Breakdown> approvalTimes(AnalyticsContext ctx) {
        String hours = "extract(epoch from (ce.approved_at - ce.created_at)) / 3600";
        String sql = "select case when " + hours + " < 1 then 'LT_1H' when " + hours + " < 24 then 'LT_1D' when " + hours + " < 72 then 'LT_3D' when " + hours + " < 168 then 'LT_7D' else 'GE_7D' end, count(*)"
                + CERTIFICATE_FROM + " where " + ctx.certificates("cur") + " and ce.approval_status = 'APPROVED' and ce.approved_at is not null group by 1";
        Map<String, Long> counts = new java.util.HashMap<>();
        for (Object[] r : repository.rows(sql, ctx.params)) counts.put(String.valueOf(r[0]), num(r[1]));
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (String key : List.of("LT_1H", "LT_1D", "LT_3D", "LT_7D", "GE_7D")) result.add(breakdown("approvalTime", key, null, Map.of("count", counts.getOrDefault(key, 0L))));
        return result;
    }

    /**
     * Bản đồ nhiệt đơn vị x kỳ: tối đa 8 đơn vị có nhiều phát sinh nhất, ô là số đếm trong kỳ (khóa `p:<kỳ>`).
     * Đơn vị là giáo hạt (phạm vi giáo phận), giáo xứ (phạm vi giáo hạt) hoặc cấp bậc (phạm vi giáo xứ).
     */
    private List<AnalyticsResponse.Breakdown> heat(AnalyticsContext ctx, String from, String where, String dateExpr, String aggregate) {
        String[] unit = switch (ctx.scope.type()) {
            case DIOCESE -> new String[]{"d.id::text", "d.name"};
            case DEANERY -> new String[]{"p.id::text", "p.name"};
            default -> new String[]{"l.leader_level", "coalesce(ll.level_name, l.leader_level)"};
        };
        var bucket = ctx.filters.resolvedTimeBucket();
        String period = "to_char(" + dateExpr + ", '" + bucketFormat(bucket) + "')";
        String sql = "select " + unit[0] + ", " + unit[1] + ", " + period + ", " + aggregate + from + " where " + where + " group by 1, 2, 3";
        Map<String, Map<String, Object>> cells = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            String key = String.valueOf(r[0]);
            labels.put(key, String.valueOf(r[1]));
            cells.computeIfAbsent(key, k -> new LinkedHashMap<>()).put("p:" + r[2], num(r[3]));
            totals.merge(key, num(r[3]), Long::sum);
        }
        List<String> top = totals.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(8).map(Map.Entry::getKey).toList();
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (String key : top) {
            Map<String, Object> metrics = new LinkedHashMap<>(cells.get(key));
            metrics.put("total", totals.get(key));
            result.add(breakdown("heat", key, labels.get(key), metrics));
        }
        return result;
    }

    // ----------------------------------------------------------- certificates

    record CertificateMetrics(long total, long approved, long pending, long rejected, long revoked, long suggested, long exceptionApproved,
                              BigDecimal averageHours, BigDecimal medianHours) { }

    CertificateMetrics certificateMetrics(AnalyticsContext ctx, String prefix) {
        String hours = "extract(epoch from (ce.approved_at - ce.created_at)) / 3600";
        String sql = "select count(*), count(*) filter (where ce.approval_status = 'APPROVED'), count(*) filter (where ce.approval_status in ('PENDING', 'SUBMITTED')),"
                + " count(*) filter (where ce.approval_status = 'REJECTED'), count(*) filter (where ce.approval_status = 'REVOKED'),"
                + " count(*) filter (where ce.system_suggested = true), count(*) filter (where ce.approval_status = 'APPROVED' and ce.approved_by_exception = true),"
                + " avg(" + hours + ") filter (where ce.approval_status = 'APPROVED' and ce.approved_at is not null),"
                + " percentile_cont(0.5) within group (order by " + hours + ") filter (where ce.approval_status = 'APPROVED' and ce.approved_at is not null)"
                + CERTIFICATE_FROM + " where " + ctx.certificates(prefix);
        Object[] r = repository.row(sql, ctx.params);
        return new CertificateMetrics(num(r[0]), num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6]), dec(r[7]), dec(r[8]));
    }

    /** Tỷ lệ chuyển đổi: trong các hồ sơ đạt của khóa bắt đầu trong kỳ, bao nhiêu đã có chứng nhận được duyệt. Tử số ⊆ mẫu số. */
    long[] conversion(AnalyticsContext ctx, String prefix) {
        String sql = "select count(*) filter (where " + PASSED + "),"
                + " count(*) filter (where " + PASSED + " and exists (select 1 from certificate z where z.leader_id = cp.leader_id and z.course_code = cp.course_id and z.status = true and z.approval_status = 'APPROVED'))"
                + PARTICIPATION_FROM + " where " + ctx.participations(prefix);
        Object[] r = repository.row(sql, ctx.params);
        return new long[]{num(r[0]), num(r[1])};
    }

    List<AnalyticsResponse.Trend> certificateTrend(AnalyticsContext ctx, String prefix, phucuong.ht.modules.analytics.model.PeriodRange range) {
        return trend(repository, ctx.params, CERTIFICATE_FROM, ctx.certificates(prefix), CERT_DATE, ctx.filters.resolvedTimeBucket(), range,
                List.of("total", "approved", "pending", "rejected", "revoked"),
                List.of("count(*)", "count(*) filter (where ce.approval_status = 'APPROVED')", "count(*) filter (where ce.approval_status in ('PENDING', 'SUBMITTED'))",
                        "count(*) filter (where ce.approval_status = 'REJECTED')", "count(*) filter (where ce.approval_status = 'REVOKED')"));
    }

    AnalyticsResponse certificates(AnalyticsContext ctx) {
        CertificateMetrics cur = certificateMetrics(ctx, "cur");
        CertificateMetrics prev = ctx.filters.isCompare() ? certificateMetrics(ctx, "prev") : null;
        long[] conversionCur = conversion(ctx, "cur");
        long[] conversionPrev = prev == null ? null : conversion(ctx, "prev");
        List<AnalyticsResponse.Trend> trends = certificateTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> previousTrends = prev == null ? null : certificateTrend(ctx, "prev", ctx.range.previous());

        long decided = cur.approved() + cur.rejected();
        long decidedPrev = prev == null ? 0 : prev.approved() + prev.rejected();
        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        kpis.add(count("totalCertificates", cur.total(), prev == null ? null : prev.total(), sparkline(trends, "total")));
        kpis.add(count("approved", cur.approved(), prev == null ? null : prev.approved(), sparkline(trends, "approved")));
        kpis.add(count("pending", cur.pending(), prev == null ? null : prev.pending(), sparkline(trends, "pending")));
        kpis.add(count("rejected", cur.rejected(), prev == null ? null : prev.rejected(), sparkline(trends, "rejected")));
        kpis.add(count("revoked", cur.revoked(), prev == null ? null : prev.revoked(), sparkline(trends, "revoked")));
        kpis.add(ratio("approvalRate", cur.approved(), decided, prev == null ? null : prev.approved(), prev == null ? null : decidedPrev, null));
        kpis.add(ratio("conversionRate", conversionCur[1], conversionCur[0], conversionPrev == null ? null : conversionPrev[1], conversionPrev == null ? null : conversionPrev[0], null));
        kpis.add(ratio("systemSuggestedRate", cur.suggested(), cur.total(), prev == null ? null : prev.suggested(), prev == null ? null : prev.total(), null));
        kpis.add(ratio("exceptionRate", cur.exceptionApproved(), cur.approved(), prev == null ? null : prev.exceptionApproved(), prev == null ? null : prev.approved(), null));
        kpis.add(measure("medianApprovalHours", HOURS, cur.medianHours(), prev == null ? null : prev.medianHours()));
        kpis.add(measure("averageApprovalHours", HOURS, cur.averageHours(), prev == null ? null : prev.averageHours()));

        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>(certificateStatus(ctx));
        breakdowns.addAll(certificateGroup(ctx, "course", "course"));
        breakdowns.addAll(certificateGroup(ctx, "courseType", "courseType"));
        if (ctx.scope.type() == AnalyticsScopeType.DIOCESE) breakdowns.addAll(certificateGroup(ctx, "deanery", "deanery"));
        if (ctx.scope.type() == AnalyticsScopeType.DEANERY) breakdowns.addAll(certificateGroup(ctx, "parish", "parish"));
        String groupBy = ctx.filters.getGroupBy();
        if (AnalyticsContext.has(groupBy) && participationDimension(groupBy) != null) breakdowns.addAll(certificateGroup(ctx, groupBy, "grouped"));

        breakdowns.addAll(approvalTimes(ctx));
        breakdowns.addAll(heat(ctx, CERTIFICATE_FROM, ctx.certificates("cur"), CERT_DATE, "count(*)"));
        return AnalyticsResponse.builder().kpis(kpis).breakdowns(breakdowns).trends(trends).previousTrends(previousTrends)
                .funnel(funnel(List.of("passed", "approved"), conversionCur[0], conversionCur[1])).alerts(certificateAlerts(ctx, cur)).build();
    }

    private List<AnalyticsResponse.Breakdown> certificateStatus(AnalyticsContext ctx) {
        String sql = "select coalesce(ce.approval_status, 'UNKNOWN'), count(*), count(*) filter (where ce.system_suggested = true), count(*) filter (where ce.approved_by_exception = true)"
                + CERTIFICATE_FROM + " where " + ctx.certificates("cur") + " group by 1 order by count(*) desc";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("count", num(r[1]));
            metrics.put("systemSuggested", num(r[2]));
            metrics.put("exceptionApproved", num(r[3]));
            result.add(breakdown("approvalStatus", String.valueOf(r[0]), null, metrics));
        }
        return result;
    }

    private List<AnalyticsResponse.Breakdown> certificateGroup(AnalyticsContext ctx, String dimension, String type) {
        String[] expression = participationDimension(dimension);
        String yearExpression = "extract(year from " + CERT_DATE + ")::int::text";
        String key = "year".equals(dimension) ? yearExpression : expression[0];
        String sql = "select " + key + ", max(" + expression[1] + "), count(*), count(*) filter (where ce.approval_status = 'APPROVED'),"
                + " count(*) filter (where ce.approval_status in ('PENDING', 'SUBMITTED')), count(*) filter (where ce.approval_status = 'REJECTED'),"
                + " count(*) filter (where ce.approval_status = 'REVOKED')"
                + CERTIFICATE_FROM + " where " + ctx.certificates("cur") + " group by 1 order by count(*) desc, 1 limit " + TOP_LIMIT;
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("dimension", dimension);
            metrics.put("total", num(r[2]));
            metrics.put("approved", num(r[3]));
            metrics.put("pending", num(r[4]));
            metrics.put("rejected", num(r[5]));
            metrics.put("revoked", num(r[6]));
            metrics.put("approvalRate", rate(num(r[3]), num(r[3]) + num(r[5])));
            result.add(breakdown(type, String.valueOf(r[0]), r[1] == null ? null : String.valueOf(r[1]), metrics));
        }
        return result;
    }

    private List<AnalyticsResponse.Alert> certificateAlerts(AnalyticsContext ctx, CertificateMetrics cur) {
        List<AnalyticsResponse.Alert> alerts = new ArrayList<>();
        AnalyticsResponse.Alert slow = slowCertificates(ctx);
        if (slow != null) alerts.add(slow);
        BigDecimal rejectRate = rate(cur.rejected(), cur.approved() + cur.rejected());
        if (cur.approved() + cur.rejected() >= MIN_SAMPLE && rejectRate != null && rejectRate.intValue() > HIGH_REJECT_RATE)
            alerts.add(alert("highRejectionRate", "warning", cur.rejected(), "/certificates/approvals", List.of(item("rejectionRate", null, rejectRate))));
        return alerts;
    }

    /** Chứng nhận chờ duyệt quá hạn: tính theo trạng thái hiện tại, không phụ thuộc kỳ lọc. */
    AnalyticsResponse.Alert slowCertificates(AnalyticsContext ctx) {
        String base = CERTIFICATE_FROM + " where " + ctx.leaders(false) + " and " + CERTIFICATE_VALID + ctx.courseFilters()
                + " and ce.approval_status in ('PENDING', 'SUBMITTED') and ce.created_at < now() - interval '" + SLOW_APPROVAL_DAYS + " days'";
        long count = repository.scalar("select count(*)" + base, ctx.params);
        if (count == 0) return null;
        List<AnalyticsResponse.AlertItem> items = new ArrayList<>();
        for (Object[] r : repository.rows("select ce.certificate_code, c.course_name, floor(extract(epoch from (now() - ce.created_at)) / 86400)" + base + " order by ce.created_at limit 5", ctx.params))
            items.add(item(r[0], String.valueOf(r[1]), dec(r[2])));
        return alert("slowCertificateApprovals", "critical", count, "/certificates/approvals", items);
    }
}
