package phucuong.ht.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.model.response.OpenCourseResponse;
import phucuong.ht.modules.analytics.model.response.WorkQueueItemResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static phucuong.ht.modules.analytics.service.AnalyticsContext.*;
import static phucuong.ht.modules.analytics.service.MetricSupport.*;

/** Trang tổng quan: KPI có so sánh kỳ trước, phễu, xu hướng, xếp hạng và hàng đợi việc cần xử lý. */
@Component @RequiredArgsConstructor
class OverviewAnalytics {
    private static final int TOP = 5;
    private static final int STALE_APPROVAL_DAYS = 3;
    private static final int CLOSING_DAYS = 7;
    private static final long CLOSING_HOURS = 72;

    private final AnalyticsRepository repository;
    private final TrainingAnalytics training;
    private final PeopleAnalytics people;

    AnalyticsResponse systemOverview(boolean includeInactive) {
        String dioceses = includeInactive ? "select count(*) from diocese" : "select count(*) from diocese where status = true";
        String deaneries = includeInactive ? "select count(*) from deanery"
                : "select count(*) from deanery de where de.status = true and de.diocese_id in (select di.id from diocese di where di.status = true)";
        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        Map<String, Object> none = Map.of();
        kpis.add(count("totalDioceses", repository.scalar(dioceses, none), null, null));
        kpis.add(count("totalDeaneries", repository.scalar(deaneries, none), null, null));
        kpis.add(count("totalParishes", repository.scalar(includeInactive ? "select count(*) from parish"
                : "select count(*) from parish p where p.status = true and p.deanery_id in (select d.id from deanery d join diocese di on di.id = d.diocese_id where d.status = true and di.status = true)", none), null, null));
        kpis.add(count("totalLeaders", repository.scalar(includeInactive ? "select count(*) from leader"
                : "select count(*) from leader l where l.parish_id in (select p.id from parish p join deanery d on d.id = p.deanery_id join diocese di on di.id = d.diocese_id where p.status = true and d.status = true and di.status = true)", none), null, null));
        return AnalyticsResponse.builder().kpis(kpis).build();
    }


    /**
     * Tổng quan của giáo xứ: chỉ về nhân sự và chứng nhận của chính giáo xứ. Giáo xứ không tổ chức khóa nên không có phân tích khóa
     * và không có hàng đợi duyệt; việc cần làm là đăng ký khóa đang mở (xem openCourses).
     */
    AnalyticsResponse parishOverview(AnalyticsContext ctx) {
        boolean compare = ctx.filters.isCompare();
        PeopleAnalytics.LeaderMetrics leaders = people.leaderMetrics(ctx);
        TrainingAnalytics.CertificateMetrics cert = training.certificateMetrics(ctx, "cur");
        TrainingAnalytics.CertificateMetrics certPrev = compare ? training.certificateMetrics(ctx, "prev") : null;
        List<AnalyticsResponse.Trend> trends = people.peopleTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> previousTrends = compare ? people.peopleTrend(ctx, "prev", ctx.range.previous()) : null;
        List<AnalyticsResponse.Trend> certTrends = training.certificateTrend(ctx, "cur", ctx.range);

        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        kpis.add(count("activeLeaders", leaders.active(), null, null));
        kpis.add(count("newLeaders", people.newLeaders(ctx, "cur"), compare ? people.newLeaders(ctx, "prev") : null, sparkline(trends, "newLeaders")));
        kpis.add(count("promotions", repository.scalar("select count(*) from rank_history rh join leader l on l.id = rh.leader_id" + UNITS_JOIN + " where " + ctx.leaders(false)
                + " and rh.status = true and coalesce(rh.reverted, false) = false and rh.promotion_date between :curFrom and :curTo", ctx.params),
                compare ? repository.scalar("select count(*) from rank_history rh join leader l on l.id = rh.leader_id" + UNITS_JOIN + " where " + ctx.leaders(false)
                        + " and rh.status = true and coalesce(rh.reverted, false) = false and rh.promotion_date between :prevFrom and :prevTo", ctx.params) : null, sparkline(trends, "promotions")));
        kpis.add(ratio("trainedRate", leaders.trained(), leaders.total(), null, null, null));
        kpis.add(count("approvedCertificates", cert.approved(), certPrev == null ? null : certPrev.approved(), sparkline(certTrends, "approved")));
        kpis.add(count("pending", cert.pending(), certPrev == null ? null : certPrev.pending(), sparkline(certTrends, "pending")));

        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>(people.leaderGroup(ctx, "gender", "gender", leaders.total()));
        breakdowns.addAll(people.leaderGroup(ctx, "leaderLevel", "leaderLevel", leaders.total()));
        List<AnalyticsResponse.Alert> alerts = new ArrayList<>();
        AnalyticsResponse.Alert slow = training.slowCertificates(ctx);
        if (slow != null) alerts.add(slow);
        alerts.addAll(people.leaderAlerts(leaders));
        return AnalyticsResponse.builder().kpis(kpis).breakdowns(breakdowns).trends(trends).previousTrends(previousTrends).alerts(alerts).build();
    }

    /** Khóa đang mở đăng ký, khóa sắp hết hạn lên trước. Giáo xứ thấy khóa của giáo hạt và giáo phận mình trực thuộc; cấp khác thấy khóa do mình tổ chức. */
    List<OpenCourseResponse> openCourses(AnalyticsContext ctx) {
        String visible;
        if (ctx.scope.type() == AnalyticsScopeType.PARISH) {
            Object[] parents = repository.row("select d.id, d.diocese_id from parish p join deanery d on d.id = p.deanery_id where p.id = :parishId", ctx.params);
            ctx.params.put("parentDeanery", parents[0]);
            ctx.params.put("parentDiocese", parents[1]);
            visible = "(c.deanery_id = :parentDeanery or c.diocese_id = :parentDiocese)";
        } else visible = ctx.organizedCourses();
        String registered = "(select count(*) from course_participation cp where cp.course_id = c.course_code and cp.status = true and " + PS + " not in ('CANCELLED', 'REJECTED')"
                + " and cp.leader_id in (select l.id" + LEADER_FROM + " where " + ctx.leaders(false) + "))";
        String sql = "select c.course_code, c.course_name, ct.type_name, c.location, c.start_date, c.end_date, c.registration_end_at,"
                + " floor(extract(epoch from (c.registration_end_at - now())) / 3600), " + registered
                + " from training_course c left join training_course_type ct on ct.type_code = c.course_type_code"
                + " where c.status = true and c.registration_status = 'OPEN' and c.organizing_status <> 'ENDED' and (c.registration_end_at is null or c.registration_end_at > now()) and " + visible
                + " order by c.registration_end_at nulls last, c.start_date, c.course_name";
        List<OpenCourseResponse> result = new ArrayList<>();
        for (Object[] r : repository.page(sql, ctx.params, 0, 20)) {
            Long hours = r[7] == null ? null : ((Number) r[7]).longValue();
            result.add(OpenCourseResponse.builder().courseCode(String.valueOf(r[0])).courseName(String.valueOf(r[1])).courseType(r[2] == null ? null : String.valueOf(r[2]))
                    .location(r[3] == null ? null : String.valueOf(r[3])).startDate(r[4] == null ? null : ((java.sql.Date) r[4]).toLocalDate())
                    .endDate(r[5] == null ? null : ((java.sql.Date) r[5]).toLocalDate())
                    .registrationEndAt(r[6] == null ? null : ((java.time.Instant) r[6]).atOffset(java.time.ZoneOffset.UTC))
                    .hoursLeft(hours).urgency(hours != null && hours <= CLOSING_HOURS ? "closing" : "open").registeredLeaders(num(r[8])).build());
        }
        return result;
    }

    AnalyticsResponse overview(AnalyticsContext ctx) {
        boolean compare = ctx.filters.isCompare();
        TrainingAnalytics.CourseMetrics cur = training.courseMetrics(ctx, "cur");
        TrainingAnalytics.CourseMetrics prev = compare ? training.courseMetrics(ctx, "prev") : null;
        TrainingAnalytics.CertificateMetrics cert = training.certificateMetrics(ctx, "cur");
        TrainingAnalytics.CertificateMetrics certPrev = compare ? training.certificateMetrics(ctx, "prev") : null;
        PeopleAnalytics.LeaderMetrics leaders = people.leaderMetrics(ctx);
        List<AnalyticsResponse.Trend> trends = training.courseTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> previousTrends = compare ? training.courseTrend(ctx, "prev", ctx.range.previous()) : null;
        List<AnalyticsResponse.Trend> certTrends = training.certificateTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> peopleTrends = people.peopleTrend(ctx, "cur", ctx.range);

        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        kpis.add(count("activeLeaders", leaders.active(), null, null));
        kpis.add(count("newLeaders", people.newLeaders(ctx, "cur"), compare ? people.newLeaders(ctx, "prev") : null, sparkline(peopleTrends, "newLeaders")));
        kpis.add(count("registrations", cur.registrations(), prev == null ? null : prev.registrations(), sparkline(trends, "registrations")));
        kpis.add(ratio("passRate", cur.passed(), cur.attended(), prev == null ? null : prev.passed(), prev == null ? null : prev.attended(), ratioSparkline(trends, "passed", "attended")));
        kpis.add(count("approvedCertificates", cert.approved(), certPrev == null ? null : certPrev.approved(), sparkline(certTrends, "approved")));
        kpis.add(count("pendingApproval", cur.pendingApproval(), prev == null ? null : prev.pendingApproval(), null));

        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>();
        breakdowns.addAll(top(training.participationGroup(ctx, "course", "course")));
        String unit = switch (ctx.scope.type()) { case DIOCESE -> "deanery"; case DEANERY -> "parish"; default -> null; };
        if (unit != null) breakdowns.addAll(top(training.participationGroup(ctx, unit, "unit")));
        breakdowns.addAll(top(training.participationGroup(ctx, "courseType", "courseType")));

        return AnalyticsResponse.builder().kpis(kpis).breakdowns(breakdowns).trends(trends).previousTrends(previousTrends)
                .funnel(training.courseFunnel(cur)).alerts(alerts(ctx)).build();
    }

    private List<AnalyticsResponse.Breakdown> top(List<AnalyticsResponse.Breakdown> rows) {
        return rows.size() > TOP ? rows.subList(0, TOP) : rows;
    }

    /** Cảnh báo theo trạng thái hiện tại (không phụ thuộc kỳ lọc), sắp xếp từ nghiêm trọng tới thông tin. */
    List<AnalyticsResponse.Alert> alerts(AnalyticsContext ctx) {
        List<AnalyticsResponse.Alert> alerts = new ArrayList<>();
        AnalyticsResponse.Alert slow = training.slowCertificates(ctx);
        if (slow != null) alerts.add(slow);

        String participations = PARTICIPATION_FROM + " where " + ctx.leaders(false) + " and " + PARTICIPATION_VALID + ctx.courseFilters();
        String stale = participations + " and cp.approval_status in ('PENDING', 'SUBMITTED') and cp.created_at < now() - interval '" + STALE_APPROVAL_DAYS + " days'";
        long staleCount = repository.scalar("select count(*)" + stale, ctx.params);
        if (staleCount > 0) alerts.add(alert("stalePendingApprovals", "critical", staleCount, "/training/approvals", courseItems("select c.course_code, c.course_name, count(*)" + stale + " group by 1, 2 order by 3 desc limit 5", ctx.params)));

        String scoring = participations + " and " + ATTENDED + " and cp.total_score is null and c.score_locked = false and c.end_date < current_date";
        long scoringCount = repository.scalar("select count(*)" + scoring, ctx.params);
        if (scoringCount > 0) alerts.add(alert("scoringOverdue", "warning", scoringCount, "/training/scores", courseItems("select c.course_code, c.course_name, count(*)" + scoring + " group by 1, 2 order by 3 desc limit 5", ctx.params)));

        String organized = ctx.organizedCourses();
        if (!"1=0".equals(organized)) {
            String closing = " from training_course c where c.status = true and " + organized + ctx.courseFilters()
                    + " and c.registration_status = 'OPEN' and c.registration_end_at between now() and now() + interval '" + CLOSING_DAYS + " days'";
            long closingCount = repository.scalar("select count(*)" + closing, ctx.params);
            if (closingCount > 0) alerts.add(alert("registrationClosingSoon", "info", closingCount, "/training/courses",
                    courseItems("select c.course_code, c.course_name, ceil(extract(epoch from (c.registration_end_at - now())) / 86400)" + closing + " order by c.registration_end_at limit 5", ctx.params)));
        }
        return alerts;
    }

    private List<AnalyticsResponse.AlertItem> courseItems(String sql, Map<String, Object> params) {
        List<AnalyticsResponse.AlertItem> items = new ArrayList<>();
        for (Object[] r : repository.rows(sql, params)) items.add(item(r[0], String.valueOf(r[1]), new BigDecimal(r[2].toString())));
        return items;
    }

    List<WorkQueueItemResponse> workQueue(AnalyticsContext ctx) {
        // Giáo xứ không duyệt hay chấm điểm: việc của họ là đăng ký khóa đang mở.
        if (ctx.scope.type() == AnalyticsScopeType.PARISH) return List.of();
        String participations = PARTICIPATION_FROM + " where " + ctx.leaders(false) + " and " + PARTICIPATION_VALID;
        long pendingRegistrations = repository.scalar("select count(*)" + participations + " and cp.approval_status in ('PENDING', 'SUBMITTED')", ctx.params);
        // Hồ sơ bị từ chối duyệt (kể cả bị xóa khỏi danh sách tham dự) chờ duyệt lại; khóa đã kết thúc thì không còn xử lý.
        long rejectedRegistrations = repository.scalar("select count(*)" + participations + " and cp.approval_status = 'REJECTED' and (c.end_date is null or c.end_date >= current_date)", ctx.params);
        long pendingScoring = repository.scalar("select count(*)" + participations + " and " + ATTENDED + " and cp.total_score is null and c.score_locked = false", ctx.params);
        long pendingCertificates = repository.scalar("select count(*)" + CERTIFICATE_FROM + " where " + ctx.leaders(false) + " and " + CERTIFICATE_VALID + " and ce.approval_status in ('PENDING', 'SUBMITTED')", ctx.params);
        String organized = ctx.organizedCourses();
        long closing = "1=0".equals(organized) ? 0 : repository.scalar("select count(*) from training_course c where c.status = true and " + organized
                + " and c.registration_status = 'OPEN' and c.registration_end_at between now() and now() + interval '" + CLOSING_DAYS + " days'", ctx.params);
        return List.of(
                work("pendingRegistrations", "/training/approvals", pendingRegistrations),
                work("rejectedRegistrations", "/training/approvals", rejectedRegistrations),
                work("pendingScoring", "/training/scores", pendingScoring),
                work("closingCourses", "/training/courses", closing),
                work("pendingCertificates", "/certificates/approvals", pendingCertificates));
    }

    private WorkQueueItemResponse work(String key, String href, long count) {
        return WorkQueueItemResponse.builder().key(key).href(href).count(count).severity(count == 0 ? "none" : "normal").build();
    }
}
