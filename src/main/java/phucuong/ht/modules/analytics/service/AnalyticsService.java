package phucuong.ht.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.model.response.WorkQueueItemResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;
import phucuong.ht.modules.analytics.security.AnalyticsScopeResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsRepository repository;
    private final AnalyticsScopeResolver scopeResolver;

    @Cacheable(cacheNames = CacheNames.ANALYTICS_OVERVIEW, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'overview', #request.fromDate, #request.toDate, #request.timeBucket, #request.groupBy)")
    public AnalyticsResponse overview(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, true);
        if (scope.isSystemOverview()) {
            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("totalDioceses", repository.scalar("select count(*) from diocese", Map.of()));
            kpis.put("totalDeaneries", repository.scalar("select count(*) from deanery", Map.of()));
            kpis.put("totalParishes", repository.scalar("select count(*) from parish", Map.of()));
            kpis.put("totalLeaders", repository.scalar("select count(*) from leader", Map.of()));
            return response(scope, filters, kpis, List.of(), List.of(), List.of());
        }

        Map<String, Object> params = repository.params(scope);
        String leaderScope = repository.leaderScope(scope, "l");
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalLeaders", count("leader l", leaderScope, "", params));
        kpis.put("activeLeaders", count("leader l", leaderScope + " and l.status = true", "", params));
        kpis.put("courseRegistrations", count("course_participation cp join leader l on l.id = cp.leader_id", leaderScope, date("cp.registration_date", filters, params), params));
        kpis.put("approvedCertificates", count("certificate c join leader l on l.id = c.leader_id", leaderScope + " and c.approval_status = 'APPROVED'", date("c.issue_date", filters, params), params));
        return response(scope, filters, kpis, List.of(), List.of(), List.of());
    }

    public List<WorkQueueItemResponse> workQueue(Authentication authentication) {
        AnalyticsScope scope = scopeResolver.resolve(authentication, true);
        Map<String, Object> params = repository.params(scope);
        String leaderScope = repository.leaderScope(scope, "l");
        String courseScope = repository.courseScope(scope, "c");
        String certificateCourseScope = repository.courseScope(scope, "tc");
        String participationFrom = "course_participation cp join training_course c on c.course_code = cp.course_id join leader l on l.id = cp.leader_id";
        String certificateFrom = "certificate cert join training_course tc on tc.course_code = cert.course_code join leader l on l.id = cert.leader_id";

        long pendingApprovals = count(
                participationFrom,
                leaderScope + " and " + courseScope + " and (cp.deanery_approval_status in ('PENDING','SUBMITTED') or cp.diocese_approval_status in ('PENDING','SUBMITTED'))",
                "",
                params
        );
        long pendingScoring = count(
                participationFrom,
                leaderScope + " and " + courseScope + " and cp.total_score is null and (cp.deanery_approval_status = 'APPROVED' or cp.diocese_approval_status = 'APPROVED')",
                "",
                params
        );
        long closingCourses = count(
                "training_course c",
                courseScope + " and c.registration_end_at is not null and c.registration_end_at between now() and now() + interval '7 days'",
                "",
                params
        );
        long pendingCertificates = count(
                certificateFrom,
                leaderScope + " and " + certificateCourseScope + " and cert.approval_status = 'PENDING'",
                "",
                params
        );

        return List.of(
                workQueueItem("pending-registrations", "Đăng ký chờ duyệt", "Rà soát hồ sơ theo cấp duyệt hiện tại.", "/training/approvals", pendingApprovals, "registrations", "rose"),
                workQueueItem("pending-scoring", "Hồ sơ chờ chấm", "Nhập điểm cho hồ sơ đã duyệt và chưa khóa chứng nhận.", "/training/scores", pendingScoring, "scoring", "blue"),
                workQueueItem("closing-courses", "Khóa sắp đóng đăng ký", "Theo dõi cửa sổ đăng ký trong 7 ngày tới.", "/training/courses", closingCourses, "courses", "violet"),
                workQueueItem("pending-certificates", "Chứng nhận chờ duyệt", "Kiểm tra passing score và lý do ngoại lệ.", "/certificates/approvals", pendingCertificates, "certificates", "emerald")
        );
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_ORGANIZATION, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'organization', #request.fromDate, #request.toDate, #request.timeBucket, #request.groupBy)")
    public AnalyticsResponse organization(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, false);
        Map<String, Object> params = repository.params(scope);
        Map<String, Object> kpis = new LinkedHashMap<>();
        if (scope.type() == AnalyticsScopeType.DIOCESE) kpis.put("totalDeaneries", repository.scalar("select count(*) from deanery where diocese_id = :dioceseId", params));
        kpis.put("totalParishes", repository.scalar("select count(*) from parish p where " + parishScope(scope, "p"), params));
        String leaderScope = repository.leaderScope(scope, "l");
        kpis.put("activeParishes", repository.scalar("select count(*) from parish p where " + parishScope(scope, "p") + " and p.status = true", params));
        kpis.put("totalLeaders", count("leader l", leaderScope, "", params));
        kpis.put("activeLeaders", count("leader l", leaderScope + " and l.status = true", "", params));
        long totalLeaders = number(kpis.get("totalLeaders"));
        long activeLeaders = number(kpis.get("activeLeaders"));
        long totalParishes = number(kpis.get("totalParishes"));
        return response(scope, filters, kpis, organizationBreakdowns(scope, params), organizationTrends(leaderScope, filters, params), organizationComparisons(leaderScope, filters, params));
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_LEADERS, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'leaders', #request.fromDate, #request.toDate, #request.timeBucket, #request.groupBy)")
    public AnalyticsResponse leaders(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, false);
        Map<String, Object> params = repository.params(scope);
        String leaderScope = repository.leaderScope(scope, "l");
        String dateFilter = date("l.created_at::date", filters, params);
        Map<String, Object> kpis = new LinkedHashMap<>();
        long totalLeaders = count("leader l", leaderScope, "", params);
        long activeLeaders = count("leader l", leaderScope + " and l.status = true", "", params);
        long inactiveLeaders = count("leader l", leaderScope + " and l.status = false", "", params);
        long maleLeaders = count("leader l", leaderScope + " and l.gender = 'NAM'", "", params);
        long femaleLeaders = count("leader l", leaderScope + " and l.gender = 'NU'", "", params);
        long youthLeaders = count("leader l", leaderScope + " and extract(year from age(current_date, l.birth_date)) < 25", "", params);
        long seniorLeaders = count("leader l", leaderScope + " and extract(year from age(current_date, l.birth_date)) >= 45", "", params);
        kpis.put("totalLeaders", totalLeaders);
        kpis.put("activeLeaders", activeLeaders);
        kpis.put("inactiveLeaders", inactiveLeaders);
        kpis.put("activeRate", rate(activeLeaders, totalLeaders));
        kpis.put("maleLeaders", maleLeaders);
        kpis.put("femaleLeaders", femaleLeaders);
        kpis.put("youthLeadersUnder25", youthLeaders);
        kpis.put("seniorLeaders45Plus", seniorLeaders);
        kpis.put("averageAge", repository.decimal("select coalesce(avg(extract(year from age(current_date, l.birth_date))), 0) from leader l where " + leaderScope, params));
        kpis.put("newLeadersInPeriod", count("leader l", leaderScope, dateFilter, params));
        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>();
        breakdowns.addAll(leaderDistribution("gender", "l.gender", leaderScope, "", params));
        breakdowns.addAll(leaderDistribution("ageGroup", "case when extract(year from age(current_date, l.birth_date)) < 18 then 'UNDER_18' when extract(year from age(current_date, l.birth_date)) between 18 and 24 then 'AGE_18_24' when extract(year from age(current_date, l.birth_date)) between 25 and 34 then 'AGE_25_34' when extract(year from age(current_date, l.birth_date)) between 35 and 44 then 'AGE_35_44' when extract(year from age(current_date, l.birth_date)) between 45 and 54 then 'AGE_45_54' else 'AGE_55_PLUS' end", leaderScope, "", params));
        breakdowns.addAll(leaderDistribution("leaderLevel", "l.leader_level", leaderScope, "", params));
        breakdowns.addAll(leaderDistribution("status", "case when l.status then 'ACTIVE' else 'INACTIVE' end", leaderScope, "", params));
        breakdowns.addAll(leaderDistribution("tenureGroup", "case when extract(year from age(current_date, l.created_at)) < 1 then 'UNDER_1_YEAR' when extract(year from age(current_date, l.created_at)) between 1 and 2 then 'YEAR_1_2' when extract(year from age(current_date, l.created_at)) between 3 and 5 then 'YEAR_3_5' else 'OVER_5_YEARS' end", leaderScope, "", params));
        breakdowns.addAll(groupedBreakdown("leader l", "l", leaderScope, "", "l.created_at::date", filters, params));
        return response(scope, filters, kpis, breakdowns, leaderTrends(leaderScope, dateFilter, filters, params), leaderComparisons(leaderScope, dateFilter, params));
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_COURSES, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'courses', #request.fromDate, #request.toDate, #request.timeBucket, #request.groupBy)")
    public AnalyticsResponse courses(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, false);
        Map<String, Object> params = repository.params(scope);
        String leaderScope = repository.leaderScope(scope, "l");
        String courseScope = repository.courseScope(scope, "c");
        String participationDate = date("cp.registration_date", filters, params);
        String courseDate = date("c.start_date", filters, params);
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalCourses", scope.type() == AnalyticsScopeType.PARISH ? 0L : count("training_course c", courseScope, courseDate, params));
        String participationFrom = "course_participation cp join training_course c on c.course_code = cp.course_id join leader l on l.id = cp.leader_id";
        String participationScope = leaderScope + " and " + courseScope;
        long registrations = count(participationFrom, participationScope, participationDate, params);
        long attended = count(participationFrom, participationScope + " and cp.participation_status in ('ATTENDED', 'COMPLETED')", participationDate, params);
        long completed = count(participationFrom, participationScope + " and cp.participation_status = 'COMPLETED'", participationDate, params);
        long passed = count(participationFrom, participationScope + " and cp.result = 'COMPLETED'", participationDate, params);
        long cancelled = count(participationFrom, participationScope + " and cp.participation_status = 'CANCELLED'", participationDate, params);
        long pending = count(participationFrom, participationScope + " and ('PENDING' in (cp.deanery_approval_status, cp.diocese_approval_status, cp.certificate_approval_status) or 'SUBMITTED' in (cp.deanery_approval_status, cp.diocese_approval_status, cp.certificate_approval_status))", participationDate, params);
        long certificateRequested = count(participationFrom, participationScope + " and cp.certificate_requested_at is not null", participationDate, params);
        long certificateApproved = count(participationFrom, participationScope + " and cp.certificate_approved_at is not null", participationDate, params);
        kpis.put("registrations", registrations); kpis.put("attended", attended); kpis.put("completed", completed); kpis.put("passed", passed); kpis.put("cancelled", cancelled); kpis.put("pendingApproval", pending);
        kpis.put("attendanceRate", rate(attended, registrations)); kpis.put("completionRate", rate(completed, registrations)); kpis.put("passRate", rate(passed, completed)); kpis.put("cancellationRate", rate(cancelled, registrations)); kpis.put("certificateRequestRate", rate(certificateRequested, passed)); kpis.put("certificateApprovalRate", rate(certificateApproved, certificateRequested));
        kpis.put("averageScore", repository.decimal("select coalesce(avg(cp.total_score), 0) from " + participationFrom + " where " + participationScope + participationDate, params));
        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>(courseBreakdown(participationScope, participationDate, params));
        breakdowns.addAll(groupedBreakdown(participationFrom, "cp", participationScope, participationDate, "cp.registration_date", filters, params));
        return response(scope, filters, kpis, breakdowns, courseTrends(participationScope, participationDate, filters, params), courseComparisons(participationScope, participationDate, params));
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_CERTIFICATES, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'certificates', #request.fromDate, #request.toDate, #request.timeBucket, #request.groupBy)")
    public AnalyticsResponse certificates(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, false);
        Map<String, Object> params = repository.params(scope);
        String leaderScope = repository.leaderScope(scope, "l");
        String courseScope = repository.courseScope(scope, "tc");
        String certificateScope = leaderScope + " and " + courseScope;
        String dateFilter = date("coalesce(c.issue_date, c.created_at::date)", filters, params);
        Map<String, Object> kpis = new LinkedHashMap<>();
        String certificateFrom = "certificate c join training_course tc on tc.course_code = c.course_code join leader l on l.id = c.leader_id";
        long total = count(certificateFrom, certificateScope, dateFilter, params);
        long approved = count(certificateFrom, certificateScope + " and c.approval_status = 'APPROVED'", dateFilter, params);
        long pending = count(certificateFrom, certificateScope + " and c.approval_status in ('PENDING', 'SUBMITTED')", dateFilter, params);
        long rejected = count(certificateFrom, certificateScope + " and c.approval_status = 'REJECTED'", dateFilter, params);
        long suggested = count(certificateFrom, certificateScope + " and c.system_suggested = true", dateFilter, params);
        long exceptionApproved = count(certificateFrom, certificateScope + " and c.approved_by_exception = true", dateFilter, params);
        long passed = count("course_participation cp join training_course tc on tc.course_code = cp.course_id join leader l on l.id = cp.leader_id", certificateScope + " and cp.result = 'COMPLETED'", date("cp.registration_date", filters, params), params);
        kpis.put("totalCertificates", total); kpis.put("approved", approved); kpis.put("pending", pending); kpis.put("rejected", rejected); kpis.put("systemSuggested", suggested); kpis.put("exceptionApproved", exceptionApproved);
        kpis.put("approvalRate", rate(approved, total)); kpis.put("conversionRate", rate(approved, passed));
        kpis.put("averageApprovalHours", repository.decimal("select coalesce(avg(extract(epoch from (c.approved_at - c.created_at)) / 3600), 0) from " + certificateFrom + " where " + certificateScope + " and c.approved_at is not null" + dateFilter, params));
        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>(certificateBreakdown(certificateScope, dateFilter, params));
        breakdowns.addAll(certificateCourseBreakdown(certificateScope, dateFilter, params));
        breakdowns.addAll(groupedBreakdown(certificateFrom, "c", certificateScope, dateFilter, "c.issue_date", filters, params));
        return response(scope, filters, kpis, breakdowns, certificateTrends(certificateScope, dateFilter, filters, params), certificateComparisons(certificateScope, dateFilter, params));
    }

    private List<AnalyticsResponse.Breakdown> courseBreakdown(String leaderScope, String dateFilter, Map<String, Object> params) {
        String sql = "select c.course_code, c.course_name, c.course_type, count(cp.id), " +
                "count(*) filter (where cp.participation_status = 'COMPLETED'), count(*) filter (where cp.result = 'COMPLETED'), coalesce(avg(cp.total_score), 0) " +
                "from course_participation cp join training_course c on c.course_code = cp.course_id join leader l on l.id = cp.leader_id where " + leaderScope + dateFilter +
                " group by c.course_code, c.course_name, c.course_type order by count(cp.id) desc, c.course_name";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] row : repository.rows(sql, params)) {
            Map<String, Object> metrics = new LinkedHashMap<>(); metrics.put("courseType", row[2]); metrics.put("registrations", number(row[3])); metrics.put("completed", number(row[4])); metrics.put("passed", number(row[5])); metrics.put("averageScore", decimal(row[6]));
            result.add(AnalyticsResponse.Breakdown.builder().type("course").key(String.valueOf(row[0])).metrics(metrics).build());
        }
        return result;
    }

    private List<AnalyticsResponse.Breakdown> certificateBreakdown(String leaderScope, String dateFilter, Map<String, Object> params) {
        String sql = "select c.approval_status, count(*), count(*) filter (where c.system_suggested = true), count(*) filter (where c.approved_by_exception = true) from certificate c join training_course tc on tc.course_code = c.course_code join leader l on l.id = c.leader_id where " + leaderScope + dateFilter + " group by c.approval_status order by count(*) desc";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] row : repository.rows(sql, params)) {
            Map<String, Object> metrics = new LinkedHashMap<>(); metrics.put("count", number(row[1])); metrics.put("systemSuggested", number(row[2])); metrics.put("exceptionApproved", number(row[3]));
            result.add(AnalyticsResponse.Breakdown.builder().type("approvalStatus").key(String.valueOf(row[0])).metrics(metrics).build());
        }
        return result;
    }

    private List<AnalyticsResponse.Breakdown> certificateCourseBreakdown(String leaderScope, String dateFilter, Map<String, Object> params) {
        String sql = "select tc.course_code, tc.course_name, tc.course_type, count(c.certificate_code), count(*) filter (where c.approval_status = 'APPROVED'), count(*) filter (where c.approval_status = 'PENDING') from certificate c join leader l on l.id = c.leader_id join training_course tc on tc.course_code = c.course_code where " + leaderScope + dateFilter + " group by tc.course_code, tc.course_name, tc.course_type order by count(c.certificate_code) desc, tc.course_name";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] row : repository.rows(sql, params)) {
            Map<String, Object> metrics = new LinkedHashMap<>(); metrics.put("courseType", row[2]); metrics.put("issued", number(row[3])); metrics.put("approved", number(row[4])); metrics.put("pending", number(row[5]));
            result.add(AnalyticsResponse.Breakdown.builder().type("certificateCourse").key(String.valueOf(row[0])).metrics(metrics).build());
        }
        return result;
    }

    private List<AnalyticsResponse.Breakdown> leaderDistribution(String type, String expression, String leaderScope, String dateFilter, Map<String, Object> params) {
        String sql = "select " + expression + ", count(*) from leader l where " + leaderScope + dateFilter + " group by " + expression + " order by count(*) desc";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] row : repository.rows(sql, params)) {
            String key = String.valueOf(row[0]);
            result.add(AnalyticsResponse.Breakdown.builder().type(type).key(key).metrics(Map.of("count", number(row[1]), "percentage", rate(number(row[1]), count("leader l", leaderScope, dateFilter, params)))).build());
        }
        return result;
    }

    private List<AnalyticsResponse.Trend> organizationTrends(String scope, AnalyticsFilterRequest filters, Map<String, Object> params) { return trend("leader l", scope, date("l.created_at::date", filters, params), "l.created_at::date", filters, params, "newLeaders"); }
    private List<AnalyticsResponse.Comparison> organizationComparisons(String scope, AnalyticsFilterRequest filters, Map<String, Object> params) { return leaderComparisons(scope, date("l.created_at::date", filters, params), params); }
    private List<AnalyticsResponse.Trend> leaderTrends(String scope, String dateFilter, AnalyticsFilterRequest filters, Map<String, Object> params) { return trend("leader l", scope, dateFilter, "l.created_at::date", filters, params, "newLeaders"); }
    private List<AnalyticsResponse.Comparison> leaderComparisons(String scope, String dateFilter, Map<String, Object> params) { return yearly("leader l", scope, dateFilter, "l.created_at::date", "count(*), count(*) filter (where l.status = true)", List.of("newLeaders", "activeLeaders"), params); }
    private List<AnalyticsResponse.Trend> courseTrends(String scope, String dateFilter, AnalyticsFilterRequest filters, Map<String, Object> params) { return trend("course_participation cp join training_course c on c.course_code = cp.course_id join leader l on l.id = cp.leader_id", scope, dateFilter, "cp.registration_date", filters, params, "registrations"); }
    private List<AnalyticsResponse.Comparison> courseComparisons(String scope, String dateFilter, Map<String, Object> params) { return yearly("course_participation cp join training_course c on c.course_code = cp.course_id join leader l on l.id = cp.leader_id", scope, dateFilter, "cp.registration_date", "count(*), count(*) filter (where cp.participation_status = 'COMPLETED'), count(*) filter (where cp.result = 'COMPLETED'), coalesce(avg(cp.total_score), 0)", List.of("registrations", "completed", "passed", "averageScore"), params); }
    private List<AnalyticsResponse.Trend> certificateTrends(String scope, String dateFilter, AnalyticsFilterRequest filters, Map<String, Object> params) { return trend("certificate c join training_course tc on tc.course_code = c.course_code join leader l on l.id = c.leader_id", scope, dateFilter, "coalesce(c.issue_date, c.created_at::date)", filters, params, "certificateRecords"); }
    private List<AnalyticsResponse.Comparison> certificateComparisons(String scope, String dateFilter, Map<String, Object> params) { return yearly("certificate c join training_course tc on tc.course_code = c.course_code join leader l on l.id = c.leader_id", scope, dateFilter, "coalesce(c.issue_date, c.created_at::date)", "count(*), count(*) filter (where c.approval_status = 'APPROVED'), count(*) filter (where c.approval_status = 'REJECTED')", List.of("records", "approved", "rejected"), params); }

    private List<AnalyticsResponse.Comparison> yearly(String from, String scope, String dateFilter, String field, String aggregates, List<String> names, Map<String, Object> params) {
        String sql = "select extract(year from " + field + ")::int, " + aggregates + " from " + from + " where " + scope + dateFilter + " group by extract(year from " + field + ") order by extract(year from " + field + ")";
        List<AnalyticsResponse.Comparison> result = new ArrayList<>();
        Map<String, Long> previous = new LinkedHashMap<>();
        for (Object[] row : repository.rows(sql, params)) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index); Object value = row[index + 1];
                if (name.equals("averageScore")) { metrics.put(name, decimal(value)); continue; }
                long current = number(value); metrics.put(name, current);
                metrics.put(name + "GrowthRate", previous.containsKey(name) ? rate(current - previous.get(name), previous.get(name)) : null);
                previous.put(name, current);
            }
            result.add(AnalyticsResponse.Comparison.builder().period(String.valueOf(row[0])).metrics(metrics).build());
        }
        return result;
    }

    private List<AnalyticsResponse.Trend> trend(String from, String scope, String dateFilter, String field, AnalyticsFilterRequest filters, Map<String, Object> params, String metric) {
        String period = repository.periodExpression(field, filters.resolvedTimeBucket());
        String sql = "select " + period + ", count(*) from " + from + " where " + scope + dateFilter + " group by " + period + " order by " + period;
        List<AnalyticsResponse.Trend> result = new ArrayList<>(); for (Object[] row : repository.rows(sql, params)) result.add(AnalyticsResponse.Trend.builder().period(String.valueOf(row[0])).metrics(Map.of(metric, number(row[1]))).build()); return result;
    }

    private List<AnalyticsResponse.Breakdown> groupedBreakdown(String from, String alias, String scope, String dateFilter, String field, AnalyticsFilterRequest filters, Map<String, Object> params) {
        String group = filters.getGroupBy(); if (group == null || group.isBlank()) return List.of();
        String expression = switch (group) { case "gender" -> "l.gender"; case "leaderLevel" -> "l.leader_level"; case "status" -> alias + ".status"; case "approvalStatus" -> alias + ".approval_status"; case "courseType" -> "c.course_type"; case "year" -> "extract(year from " + field + ")"; case "parish" -> "p.name"; case "deanery" -> "d.name"; default -> null; };
        if (expression == null) return List.of();
        String joins = (group.equals("parish") || group.equals("deanery")) ? " join parish p on p.id = l.parish_id" + (group.equals("deanery") ? " join deanery d on d.id = p.deanery_id" : "") : "";
        String sql = "select " + expression + ", count(*) from " + from + joins + " where " + scope + dateFilter + " group by " + expression + " order by count(*) desc";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>(); for (Object[] row : repository.rows(sql, params)) result.add(AnalyticsResponse.Breakdown.builder().type(group).key(String.valueOf(row[0])).metrics(Map.of("count", number(row[1]))).build()); return result;
    }

    private List<AnalyticsResponse.Breakdown> organizationBreakdowns(AnalyticsScope scope, Map<String, Object> params) {
        String sql; String type;
        if (scope.type() == AnalyticsScopeType.DIOCESE) { sql = "select d.id, d.name, count(distinct p.id), count(l.id) from deanery d left join parish p on p.deanery_id = d.id left join leader l on l.parish_id = p.id where d.diocese_id = :dioceseId group by d.id, d.name order by d.name"; type = "deanery"; }
        else if (scope.type() == AnalyticsScopeType.DEANERY) { sql = "select p.id, p.name, count(l.id) from parish p left join leader l on l.parish_id = p.id where p.deanery_id = :deaneryId group by p.id, p.name order by p.name"; type = "parish"; }
        else return List.of();
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>(); for (Object[] row : repository.rows(sql, params)) { Map<String, Object> metrics = new LinkedHashMap<>(); if (scope.type() == AnalyticsScopeType.DIOCESE) { metrics.put("totalParishes", number(row[2])); metrics.put("totalLeaders", number(row[3])); } else metrics.put("totalLeaders", number(row[2])); result.add(AnalyticsResponse.Breakdown.builder().type(type).key(String.valueOf(row[0])).metrics(metrics).build()); } return result;
    }

    private long count(String from, String scope, String dateFilter, Map<String, Object> params) { return repository.scalar("select count(*) from " + from + " where " + scope + dateFilter, params); }
    private WorkQueueItemResponse workQueueItem(String key, String title, String description, String href, long count, String icon, String tone) {
        return WorkQueueItemResponse.builder().key(key).title(title).description(description).href(href).count(count).icon(icon).tone(tone).build();
    }
    private String date(String field, AnalyticsFilterRequest filters, Map<String, Object> params) { return repository.dateFilter(field, filters, params); }
    private AnalyticsFilterRequest validate(AnalyticsFilterRequest request) { AnalyticsFilterRequest result = request == null ? new AnalyticsFilterRequest() : request; result.validate(); return result; }
    private String parishScope(AnalyticsScope scope, String alias) { return switch (scope.type()) { case DIOCESE -> alias + ".deanery_id in (select id from deanery where diocese_id = :dioceseId)"; case DEANERY -> alias + ".deanery_id = :deaneryId"; case PARISH -> alias + ".id = :parishId"; case ALL -> "1=1"; }; }
    private long number(Object value) { return value == null ? 0L : ((Number) value).longValue(); }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private BigDecimal rate(long numerator, long denominator) { return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP); }
    private AnalyticsResponse response(AnalyticsScope scope, AnalyticsFilterRequest filters, Map<String, Object> kpis, List<AnalyticsResponse.Breakdown> breakdowns, List<AnalyticsResponse.Trend> trends, List<AnalyticsResponse.Comparison> comparisons) { return AnalyticsResponse.builder().scope(scope.type().name().toLowerCase()).filters(AnalyticsResponse.Filters.builder().fromDate(filters.getFromDate()).toDate(filters.getToDate()).timeBucket(filters.resolvedTimeBucket().name().toLowerCase()).groupBy(filters.getGroupBy()).build()).kpis(kpis).breakdowns(breakdowns).trends(trends).comparisons(comparisons).build(); }
}
