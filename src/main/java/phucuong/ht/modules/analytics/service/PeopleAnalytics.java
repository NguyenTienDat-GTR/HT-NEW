package phucuong.ht.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.PeriodRange;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static phucuong.ht.modules.analytics.service.AnalyticsContext.*;
import static phucuong.ht.modules.analytics.service.MetricSupport.*;

/**
 * Phân tích nhân sự và tổ chức.
 * Cơ cấu (giới tính, tuổi, cấp bậc, đơn vị) là ảnh chụp hiện tại; "huynh trưởng mới" và "thăng cấp" là sự kiện theo kỳ.
 * Ngày tạo hồ sơ huynh trưởng là ngày nhập vào hệ thống, không phải ngày gia nhập phong trào.
 */
@Component @RequiredArgsConstructor
class PeopleAnalytics {
    private static final String AGE = "date_part('year', age(current_date, l.birth_date))";
    private static final String AGE_GROUP = "case when " + AGE + " < 18 then 'UNDER_18' when " + AGE + " < 25 then 'AGE_18_24' when " + AGE + " < 35 then 'AGE_25_34'"
            + " when " + AGE + " < 45 then 'AGE_35_44' when " + AGE + " < 55 then 'AGE_45_54' else 'AGE_55_PLUS' end";
    private static final List<String> AGE_ORDER = List.of("UNDER_18", "AGE_18_24", "AGE_25_34", "AGE_35_44", "AGE_45_54", "AGE_55_PLUS");
    private static final String CREATED = "(l.created_at at time zone " + TZ + ")::date";
    private static final String TRAINED = "exists (select 1 from course_participation x where x.leader_id = l.id and x.status = true"
            + " and coalesce(x.participation_status, 'REGISTERED') in ('ATTENDED', 'COMPLETED') and x.result = 'COMPLETED')";
    private static final int TOP_LIMIT = 15;
    private static final int LOW_ACTIVE_RATE = 80;
    private static final int LOW_YOUTH_RATE = 10;
    private static final int GENDER_IMBALANCE_RATE = 30;

    private final AnalyticsRepository repository;

    record LeaderMetrics(long total, long active, long male, long female, long youth, long senior, BigDecimal averageAge, long trained) { }

    LeaderMetrics leaderMetrics(AnalyticsContext ctx) {
        String sql = "select count(*), count(*) filter (where l.status = true), count(*) filter (where l.gender = 'NAM'), count(*) filter (where l.gender = 'NU'),"
                + " count(*) filter (where " + AGE + " < 25), count(*) filter (where " + AGE + " >= 45), avg(" + AGE + "), count(*) filter (where " + TRAINED + ")"
                + LEADER_FROM + " where " + ctx.leaders(true);
        Object[] r = repository.row(sql, ctx.params);
        return new LeaderMetrics(num(r[0]), num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), dec(r[6]), num(r[7]));
    }

    long newLeaders(AnalyticsContext ctx, String prefix) {
        return repository.scalar("select count(*)" + LEADER_FROM + " where " + ctx.leaders(false) + " and " + CREATED + " between :" + prefix + "From and :" + prefix + "To", ctx.params);
    }

    private long promotions(AnalyticsContext ctx, String prefix) {
        return repository.scalar("select count(*) from rank_history rh join leader l on l.id = rh.leader_id" + UNITS_JOIN + " where " + ctx.leaders(false)
                + " and rh.status = true and coalesce(rh.reverted, false) = false and rh.promotion_date between :" + prefix + "From and :" + prefix + "To", ctx.params);
    }

    /** Sự kiện theo kỳ: huynh trưởng mới và thăng cấp, gộp thành một chuỗi liên tục. */
    List<AnalyticsResponse.Trend> peopleTrend(AnalyticsContext ctx, String prefix, PeriodRange range) {
        var bucket = ctx.filters.resolvedTimeBucket();
        List<AnalyticsResponse.Trend> created = trend(repository, ctx.params, LEADER_FROM,
                ctx.leaders(false) + " and " + CREATED + " between :" + prefix + "From and :" + prefix + "To", CREATED, bucket, range, List.of("newLeaders"), List.of("count(*)"));
        List<AnalyticsResponse.Trend> promoted = trend(repository, ctx.params, " from rank_history rh join leader l on l.id = rh.leader_id" + UNITS_JOIN,
                ctx.leaders(false) + " and rh.status = true and coalesce(rh.reverted, false) = false and rh.promotion_date between :" + prefix + "From and :" + prefix + "To",
                "rh.promotion_date", bucket, range, List.of("promotions"), List.of("count(*)"));
        List<AnalyticsResponse.Trend> merged = new ArrayList<>();
        // Hồ sơ tích lũy = số hồ sơ đã tạo trước kỳ + cộng dồn hồ sơ mới; không phản ánh huynh trưởng đã nghỉ vì hệ thống không lưu lịch sử trạng thái.
        long running = repository.scalar("select count(*)" + LEADER_FROM + " where " + ctx.leaders(false) + " and " + CREATED + " < :" + prefix + "From", ctx.params);
        for (int i = 0; i < created.size(); i++) {
            Map<String, Object> metrics = new LinkedHashMap<>(created.get(i).getMetrics());
            metrics.putAll(promoted.get(i).getMetrics());
            running += num(metrics.get("newLeaders"));
            metrics.put("cumulative", running);
            merged.add(AnalyticsResponse.Trend.builder().period(created.get(i).getPeriod()).metrics(metrics).build());
        }
        return merged;
    }

    // ---------------------------------------------------------------- leaders

    AnalyticsResponse leaders(AnalyticsContext ctx) {
        LeaderMetrics m = leaderMetrics(ctx);
        boolean compare = ctx.filters.isCompare();
        List<AnalyticsResponse.Trend> trends = peopleTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> previousTrends = compare ? peopleTrend(ctx, "prev", ctx.range.previous()) : null;

        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        kpis.add(count("totalLeaders", m.total(), null, null));
        kpis.add(ratio("activeRate", m.active(), m.total(), null, null, null));
        kpis.add(ratio("trainedRate", m.trained(), m.total(), null, null, null));
        kpis.add(count("newLeaders", newLeaders(ctx, "cur"), compare ? newLeaders(ctx, "prev") : null, sparkline(trends, "newLeaders")));
        kpis.add(count("promotions", promotions(ctx, "cur"), compare ? promotions(ctx, "prev") : null, sparkline(trends, "promotions")));
        kpis.add(ratio("femaleRate", m.female(), m.male() + m.female(), null, null, null));
        kpis.add(measure("averageAge", SCORE, m.averageAge(), null));
        kpis.add(ratio("youthRate", m.youth(), m.total(), null, null, null));
        kpis.add(ratio("seniorRate", m.senior(), m.total(), null, null, null));

        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>();
        for (String dimension : List.of("gender", "ageGroup", "leaderLevel", "status")) breakdowns.addAll(leaderGroup(ctx, dimension, dimension, m.total()));
        if (ctx.scope.type() == AnalyticsScopeType.DIOCESE) breakdowns.addAll(leaderGroup(ctx, "deanery", "deanery", m.total()));
        if (ctx.scope.type() == AnalyticsScopeType.DIOCESE || ctx.scope.type() == AnalyticsScopeType.DEANERY) breakdowns.addAll(leaderGroup(ctx, "parish", "parish", m.total()));
        String groupBy = ctx.filters.getGroupBy();
        if (has(groupBy) && leaderDimension(groupBy) != null) breakdowns.addAll(leaderGroup(ctx, groupBy, "grouped", m.total()));
        breakdowns.addAll(agePyramid(ctx));
        breakdowns.addAll(promotionLevels(ctx));
        return AnalyticsResponse.builder().kpis(kpis).breakdowns(breakdowns).trends(trends).previousTrends(previousTrends).alerts(leaderAlerts(m)).build();
    }

    private static String[] leaderDimension(String dimension) {
        return switch (dimension) {
            case "gender" -> new String[]{"coalesce(l.gender, 'UNKNOWN')", "null::text"};
            case "ageGroup" -> new String[]{AGE_GROUP, "null::text"};
            case "leaderLevel" -> new String[]{"l.leader_level", "ll.level_name"};
            case "status" -> new String[]{"case when l.status then 'ACTIVE' else 'INACTIVE' end", "null::text"};
            case "deanery" -> new String[]{"d.id::text", "d.name"};
            case "parish" -> new String[]{"p.id::text", "p.name"};
            case "year" -> new String[]{"extract(year from " + CREATED + ")::int::text", "null::text"};
            default -> null;
        };
    }

    List<AnalyticsResponse.Breakdown> leaderGroup(AnalyticsContext ctx, String dimension, String type, long total) {
        String[] expression = leaderDimension(dimension);
        String sql = "select " + expression[0] + ", max(" + expression[1] + "), count(*), count(*) filter (where l.status = true), count(*) filter (where l.gender = 'NU'),"
                + " count(*) filter (where " + TRAINED + ")" + LEADER_FROM + " where " + ctx.leaders(true) + " group by 1 order by count(*) desc, 1 limit " + TOP_LIMIT;
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("dimension", dimension);
            metrics.put("count", num(r[2]));
            metrics.put("share", rateOrZero(num(r[2]), total));
            metrics.put("active", num(r[3]));
            metrics.put("female", num(r[4]));
            metrics.put("trained", num(r[5]));
            result.add(breakdown(type, String.valueOf(r[0]), r[1] == null ? null : String.valueOf(r[1]), metrics));
        }
        if ("ageGroup".equals(dimension)) result.sort(Comparator.comparingInt(b -> AGE_ORDER.indexOf(b.getKey())));
        return result;
    }

    /** Tháp dân số: mỗi nhóm tuổi có số nam và nữ (chưa ghi giới tính tính riêng) để vẽ hai nửa đối xứng. */
    private List<AnalyticsResponse.Breakdown> agePyramid(AnalyticsContext ctx) {
        String sql = "select " + AGE_GROUP + ", count(*) filter (where l.gender = 'NAM'), count(*) filter (where l.gender = 'NU'), count(*) filter (where l.gender is null or l.gender not in ('NAM', 'NU'))"
                + LEADER_FROM + " where " + ctx.leaders(true) + " group by 1";
        Map<String, long[]> byGroup = new HashMap<>();
        for (Object[] r : repository.rows(sql, ctx.params)) byGroup.put(String.valueOf(r[0]), new long[]{num(r[1]), num(r[2]), num(r[3])});
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (String group : AGE_ORDER) {
            long[] v = byGroup.getOrDefault(group, new long[]{0, 0, 0});
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("male", v[0]);
            metrics.put("female", v[1]);
            metrics.put("unknown", v[2]);
            result.add(breakdown("agePyramid", group, null, metrics));
        }
        return result;
    }

    /** Thăng cấp trong kỳ theo cấp bậc đạt được (bỏ các lượt đã hoàn tác). */
    private List<AnalyticsResponse.Breakdown> promotionLevels(AnalyticsContext ctx) {
        String sql = "select rh.new_level, max(nl.level_name), count(*) from rank_history rh join leader l on l.id = rh.leader_id" + UNITS_JOIN
                + " left join leader_level nl on nl.level_code = rh.new_level where " + ctx.leaders(false)
                + " and rh.status = true and coalesce(rh.reverted, false) = false and rh.promotion_date between :curFrom and :curTo group by 1 order by count(*) desc";
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) result.add(breakdown("promotionLevel", String.valueOf(r[0]), r[1] == null ? null : String.valueOf(r[1]), Map.of("count", num(r[2]))));
        return result;
    }

    List<AnalyticsResponse.Alert> leaderAlerts(LeaderMetrics m) {
        List<AnalyticsResponse.Alert> alerts = new ArrayList<>();
        if (m.total() < TrainingAnalytics.MIN_SAMPLE) return alerts;
        BigDecimal active = rate(m.active(), m.total());
        if (active != null && active.intValue() < LOW_ACTIVE_RATE) alerts.add(alert("lowActiveRate", "warning", m.total() - m.active(), "/leaders", List.of(item("activeRate", null, active))));
        BigDecimal youth = rate(m.youth(), m.total());
        if (youth != null && youth.intValue() < LOW_YOUTH_RATE) alerts.add(alert("youthShortage", "info", m.youth(), "/leaders", List.of(item("youthRate", null, youth))));
        long known = m.male() + m.female();
        BigDecimal minority = known == 0 ? null : rate(Math.min(m.male(), m.female()), known);
        if (minority != null && minority.intValue() < GENDER_IMBALANCE_RATE) alerts.add(alert("genderImbalance", "info", Math.min(m.male(), m.female()), "/leaders", List.of(item("minorityRate", null, minority))));
        return alerts;
    }

    // ----------------------------------------------------------- organization

    private String unitScope(AnalyticsContext ctx) {
        return ctx.scope.type() == AnalyticsScopeType.PARISH ? "p.id = :parishId" : ctx.scopeLeaders();
    }

    private String unitFilters(AnalyticsContext ctx) {
        StringBuilder sql = new StringBuilder();
        if (ctx.filters.getDeaneryId() != null) sql.append(" and d.id = :fDeanery");
        if (ctx.filters.getParishId() != null) sql.append(" and p.id = :fParish");
        return sql.toString();
    }

    AnalyticsResponse organization(AnalyticsContext ctx) {
        boolean includeInactive = ctx.filters.isIncludeInactiveUnits();
        String unitsFrom = " from parish p join deanery d on d.id = p.deanery_id join diocese di on di.id = d.diocese_id where " + unitScope(ctx) + unitFilters(ctx);
        String operational = "(" + AnalyticsContext.operational() + ")";
        Object[] u = repository.row("select count(distinct d.id) filter (where d.status = true and di.status = true), count(distinct d.id) filter (where not (d.status = true and di.status = true)),"
                + " count(distinct p.id) filter (where " + operational + "), count(distinct p.id) filter (where not " + operational + "),"
                + " count(distinct p.id) filter (where " + operational + " and not exists (select 1 from leader x where x.parish_id = p.id and x.status = true))"
                + unitsFrom, ctx.params);
        LeaderMetrics m = leaderMetrics(ctx);
        long activeParishes = num(u[2]);
        long inactiveParishes = num(u[3]);
        long totalParishes = includeInactive ? activeParishes + inactiveParishes : activeParishes;
        boolean compare = ctx.filters.isCompare();
        List<AnalyticsResponse.Trend> trends = peopleTrend(ctx, "cur", ctx.range);
        List<AnalyticsResponse.Trend> previousTrends = compare ? peopleTrend(ctx, "prev", ctx.range.previous()) : null;

        List<AnalyticsResponse.Kpi> kpis = new ArrayList<>();
        if (ctx.scope.type() == AnalyticsScopeType.DIOCESE) {
            kpis.add(count("totalDeaneries", includeInactive ? num(u[0]) + num(u[1]) : num(u[0]), null, null));
            kpis.add(count("inactiveDeaneries", num(u[1]), null, null));
        }
        if (ctx.scope.type() != AnalyticsScopeType.PARISH) {
            kpis.add(count("totalParishes", totalParishes, null, null));
            kpis.add(count("inactiveParishes", inactiveParishes, null, null));
            kpis.add(count("parishesWithoutLeaders", num(u[4]), null, null));
            kpis.add(measure("leadersPerParish", SCORE, totalParishes == 0 ? null : BigDecimal.valueOf(m.total()).divide(BigDecimal.valueOf(Math.max(1, activeParishes)), 1, java.math.RoundingMode.HALF_UP), null));
        }
        kpis.add(count("totalLeaders", m.total(), null, null));
        kpis.add(ratio("activeRate", m.active(), m.total(), null, null, null));
        kpis.add(ratio("trainedRate", m.trained(), m.total(), null, null, null));
        kpis.add(count("newLeaders", newLeaders(ctx, "cur"), compare ? newLeaders(ctx, "prev") : null, sparkline(trends, "newLeaders")));

        List<AnalyticsResponse.Breakdown> breakdowns = new ArrayList<>(unitRows(ctx, includeInactive));
        breakdowns.addAll(leaderGroup(ctx, "leaderLevel", "leaderLevel", m.total()));
        breakdowns.addAll(agePyramid(ctx));
        breakdowns.addAll(promotionLevels(ctx));
        List<AnalyticsResponse.Alert> alerts = organizationAlerts(ctx, unitsFrom, operational, inactiveParishes);
        return AnalyticsResponse.builder().kpis(kpis).breakdowns(breakdowns).trends(trends).previousTrends(previousTrends).alerts(alerts).build();
    }

    /** Bảng đơn vị con: giáo hạt (phạm vi giáo phận) hoặc giáo xứ (phạm vi giáo hạt); kèm huấn luyện trong kỳ để xếp hạng. */
    private List<AnalyticsResponse.Breakdown> unitRows(AnalyticsContext ctx, boolean includeInactive) {
        boolean diocese = ctx.scope.type() == AnalyticsScopeType.DIOCESE;
        if (!diocese && ctx.scope.type() != AnalyticsScopeType.DEANERY) return List.of();
        String type = diocese ? "deanery" : "parish";
        String key = diocese ? "d.id" : "p.id";
        String name = diocese ? "d.name" : "p.name";
        StringBuilder leaderJoin = new StringBuilder(" left join leader l on l.parish_id = p.id");
        if (ctx.filters.getGender() != null && !ctx.filters.getGender().isBlank()) leaderJoin.append(" and l.gender = :fGender");
        if (ctx.filters.getLeaderLevel() != null && !ctx.filters.getLeaderLevel().isBlank()) leaderJoin.append(" and l.leader_level = :fLevel");
        String sql = "select " + key + ", " + name + ", bool_and(d.status and p.status and di.status), count(distinct p.id), count(l.id), count(l.id) filter (where l.status = true),"
                + " count(l.id) filter (where " + TRAINED + ")"
                + " from deanery d join diocese di on di.id = d.diocese_id left join parish p on p.deanery_id = d.id" + (includeInactive ? "" : " and p.status = true")
                + leaderJoin + " where " + (diocese ? "d.diocese_id = :dioceseId" : "d.id = :deaneryId") + (includeInactive ? "" : " and d.status = true")
                + (diocese ? "" : " and p.id is not null") + unitFilters(ctx) + " group by " + key + ", " + name + " order by " + name;
        Map<String, long[]> training = unitTraining(ctx, diocese);
        List<AnalyticsResponse.Breakdown> result = new ArrayList<>();
        for (Object[] r : repository.rows(sql, ctx.params)) {
            long[] t = training.getOrDefault(String.valueOf(r[0]), new long[]{0, 0, 0});
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("operational", r[2] == null || Boolean.TRUE.equals(r[2]));
            metrics.put("parishes", diocese ? num(r[3]) : null);
            metrics.put("leaders", num(r[4]));
            metrics.put("activeLeaders", num(r[5]));
            metrics.put("trainedLeaders", num(r[6]));
            metrics.put("registrations", t[0]);
            metrics.put("attended", t[1]);
            metrics.put("passed", t[2]);
            metrics.put("passRate", rate(t[2], t[1]));
            result.add(breakdown("unit", String.valueOf(r[0]), String.valueOf(r[1]), metrics));
        }
        return result;
    }

    private Map<String, long[]> unitTraining(AnalyticsContext ctx, boolean diocese) {
        String key = diocese ? "d.id" : "p.id";
        String sql = "select " + key + "::text, count(*), count(*) filter (where " + ATTENDED + "), count(*) filter (where " + PASSED + ")"
                + PARTICIPATION_FROM + " where " + ctx.participations("cur") + " group by 1";
        Map<String, long[]> result = new HashMap<>();
        for (Object[] r : repository.rows(sql, ctx.params)) result.put(String.valueOf(r[0]), new long[]{num(r[1]), num(r[2]), num(r[3])});
        return result;
    }

    private List<AnalyticsResponse.Alert> organizationAlerts(AnalyticsContext ctx, String unitsFrom, String operational, long inactiveParishes) {
        List<AnalyticsResponse.Alert> alerts = new ArrayList<>();
        if (ctx.scope.type() == AnalyticsScopeType.PARISH) return alerts;
        String empty = unitsFrom + " and " + operational + " and not exists (select 1 from leader x where x.parish_id = p.id and x.status = true)";
        long count = repository.scalar("select count(*)" + empty, ctx.params);
        if (count > 0) {
            List<AnalyticsResponse.AlertItem> items = new ArrayList<>();
            for (Object[] r : repository.rows("select p.id, p.name" + empty + " order by p.name limit 5", ctx.params)) items.add(item(r[0], String.valueOf(r[1]), null));
            alerts.add(alert("parishesWithoutLeaders", "warning", count, "/organizations", items));
        }
        if (inactiveParishes > 0) alerts.add(alert("inactiveUnits", "info", inactiveParishes, "/organizations", List.of()));
        return alerts;
    }
}
