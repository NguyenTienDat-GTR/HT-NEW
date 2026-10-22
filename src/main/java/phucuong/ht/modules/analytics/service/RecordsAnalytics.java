package phucuong.ht.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.request.AnalyticsRecordRequest;
import phucuong.ht.modules.analytics.model.response.AnalyticsFilterOptionsResponse;
import phucuong.ht.modules.analytics.model.response.AnalyticsRecordsResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static phucuong.ht.modules.analytics.service.AnalyticsContext.*;

/** Danh sách chi tiết cho thao tác drill-down/xuất, và danh mục lựa chọn bộ lọc trong phạm vi quyền. */
@Component @RequiredArgsConstructor
class RecordsAnalytics {
    private static final Set<String> AGE_GROUPS = Set.of("UNDER_18", "AGE_18_24", "AGE_25_34", "AGE_35_44", "AGE_45_54", "AGE_55_PLUS");
    private static final Set<String> FUNNEL_STEPS = Set.copyOf(TrainingAnalytics.FUNNEL_KEYS);
    // Tên huynh trưởng luôn kèm tên thánh: "Giuse Nguyễn Văn A".
    private static final String LEADER_NAME = "trim(coalesce(l.holy_name, '') || ' ' || l.last_name || ' ' || l.first_name)";
    private static final String AGE = "date_part('year', age(current_date, l.birth_date))";
    private static final String CREATED = "(l.created_at at time zone " + TZ + ")::date";
    private static final int OPTION_LIMIT = 300;

    private final AnalyticsRepository repository;

    AnalyticsRecordsResponse records(AnalyticsContext ctx, AnalyticsRecordRequest request, int offset, int limit) {
        String dataset = request.getDataset();
        if (ctx.scope.type() == AnalyticsScopeType.PARISH && "participations".equals(dataset)) throw new phucuong.ht.modules.analytics.security.AnalyticsAccessDeniedException("Phạm vi giáo xứ không xem danh sách hồ sơ đăng ký khóa");
        String from;
        String where;
        String select;
        String order;
        List<String> columns;
        switch (dataset) {
            case "leaders" -> {
                from = LEADER_FROM;
                where = ctx.leaders(true);
                select = "l.id::text, " + LEADER_NAME + ", l.gender, l.birth_date, ll.level_name, p.name, d.name, l.status, " + CREATED;
                order = " order by " + LEADER_NAME + ", l.id";
                columns = List.of("id", "name", "gender", "birthDate", "leaderLevel", "parish", "deanery", "active", "createdDate");
            }
            case "certificates" -> {
                from = CERTIFICATE_FROM;
                where = ctx.certificates("cur");
                select = "ce.certificate_code, ce.certificate_code, " + LEADER_NAME + ", ll.level_name, c.course_name, ct.type_name, p.name, d.name, " + CERT_DATE + ", ce.approval_status, ce.system_suggested, ce.approved_by_exception";
                order = " order by " + CERT_DATE + " desc, ce.certificate_code";
                columns = List.of("id", "code", "leaderName", "leaderLevel", "courseName", "courseType", "parish", "deanery", "issueDate", "approvalStatus", "systemSuggested", "exceptionApproved");
            }
            default -> {
                from = PARTICIPATION_FROM;
                where = ctx.participations("cur");
                select = "cp.id, " + LEADER_NAME + ", ll.level_name, c.course_name, ct.type_name, p.name, d.name, c.start_date, c.end_date, " + PS + ", cp.result, cp.total_score, cp.approval_status, cp.certificate_approval_status, cp.registration_date";
                order = " order by c.start_date desc, " + LEADER_NAME + ", cp.id";
                columns = List.of("id", "leaderName", "leaderLevel", "courseName", "courseType", "parish", "deanery", "startDate", "endDate", "participationStatus", "result", "score", "approvalStatus", "certificateStatus", "registrationDate");
            }
        }
        if (request.getDimension() != null) where += " and " + dimension(dataset, request.getDimension(), request.getValue(), ctx.params);
        long total = repository.scalar("select count(*)" + from + " where " + where, ctx.params);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : repository.page("select " + select + from + " where " + where + order, ctx.params, offset, limit)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) row.put(columns.get(i), r[i] == null ? null : (r[i] instanceof Number || r[i] instanceof Boolean ? r[i] : r[i].toString()));
            rows.add(row);
        }
        return AnalyticsRecordsResponse.builder().dataset(dataset).columns(columns).rows(rows).total(total).page(request.getPage()).size(limit).build();
    }

    /** Chỉ nhận các dimension trong danh sách trắng; giá trị luôn đi qua tham số, kiểu được kiểm tra trước khi dùng. */
    private String dimension(String dataset, String dimension, String value, Map<String, Object> params) {
        boolean leaders = "leaders".equals(dataset);
        boolean certificates = "certificates".equals(dataset);
        switch (dimension) {
            case "deanery" -> { params.put("dv", uuid(value)); return "d.id = :dv"; }
            case "parish" -> { params.put("dv", uuid(value)); return "p.id = :dv"; }
            case "gender" -> { params.put("dv", value); return "coalesce(l.gender, 'UNKNOWN') = :dv"; }
            case "leaderLevel" -> { params.put("dv", value); return "l.leader_level = :dv"; }
            default -> { }
        }
        if (leaders) {
            switch (dimension) {
                case "ageGroup" -> {
                    if (!AGE_GROUPS.contains(value)) throw new IllegalArgumentException("Giá trị nhóm tuổi không hợp lệ");
                    params.put("dv", value);
                    return "(case when " + AGE + " < 18 then 'UNDER_18' when " + AGE + " < 25 then 'AGE_18_24' when " + AGE + " < 35 then 'AGE_25_34' when " + AGE + " < 45 then 'AGE_35_44' when " + AGE + " < 55 then 'AGE_45_54' else 'AGE_55_PLUS' end) = :dv";
                }
                case "status" -> { params.put("dv", value); return "(case when l.status then 'ACTIVE' else 'INACTIVE' end) = :dv"; }
                case "year" -> { params.put("dv", integer(value)); return "extract(year from " + CREATED + ")::int = :dv"; }
                default -> throw new IllegalArgumentException("dimension không hỗ trợ cho dataset leaders");
            }
        }
        switch (dimension) {
            case "courseType" -> { params.put("dv", value); return "coalesce(c.course_type_code, '-') = :dv"; }
            case "course" -> { params.put("dv", value); return "c.course_code = :dv"; }
            default -> { }
        }
        if (certificates) {
            switch (dimension) {
                case "approvalStatus" -> { params.put("dv", value); return "ce.approval_status = :dv"; }
                case "year" -> { params.put("dv", integer(value)); return "extract(year from " + CERT_DATE + ")::int = :dv"; }
                default -> throw new IllegalArgumentException("dimension không hỗ trợ cho dataset certificates");
            }
        }
        switch (dimension) {
            case "year" -> { params.put("dv", integer(value)); return "extract(year from c.start_date)::int = :dv"; }
            case "participationStatus" -> { params.put("dv", value); return PS + " = :dv"; }
            case "funnel" -> {
                if (!FUNNEL_STEPS.contains(value)) throw new IllegalArgumentException("Bước phễu không hợp lệ");
                return switch (value) {
                    case "valid" -> VALID;
                    case "attended" -> ATTENDED;
                    case "passed" -> PASSED;
                    case "certificate" -> PASSED + " and cp.certificate_approval_status = 'APPROVED'";
                    default -> "1=1";
                };
            }
            default -> throw new IllegalArgumentException("dimension không hỗ trợ cho dataset participations");
        }
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Mã đơn vị không hợp lệ"); }
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Giá trị năm không hợp lệ"); }
    }

    AnalyticsFilterOptionsResponse options(AnalyticsContext ctx) {
        String unitWhere = ctx.scope.type() == AnalyticsScopeType.PARISH ? "p.id = :parishId" : ctx.scopeLeaders();
        String units = " from parish p join deanery d on d.id = p.deanery_id join diocese di on di.id = d.diocese_id where " + unitWhere;
        List<AnalyticsFilterOptionsResponse.Option> deaneries = new ArrayList<>();
        for (Object[] r : repository.rows("select distinct d.id::text, d.name" + units + " order by d.name", ctx.params)) deaneries.add(option(r[0], r[1], null));
        List<AnalyticsFilterOptionsResponse.Option> parishes = new ArrayList<>();
        for (Object[] r : repository.rows("select p.id::text, p.name, d.id::text" + units + " order by p.name", ctx.params)) parishes.add(option(r[0], r[1], r[2]));
        List<AnalyticsFilterOptionsResponse.Option> types = new ArrayList<>();
        for (Object[] r : repository.rows("select type_code, type_name from training_course_type where status = true order by type_name", ctx.params)) types.add(option(r[0], r[1], null));
        List<AnalyticsFilterOptionsResponse.Option> levels = new ArrayList<>();
        for (Object[] r : repository.rows("select level_code, level_name from leader_level where status = true order by display_order", ctx.params)) levels.add(option(r[0], r[1], null));

        String participants = "select l.id" + LEADER_FROM + " where " + ctx.scopeLeaders();
        String visible = "(" + ctx.organizedCourses() + " or c.course_code in (select x.course_id from course_participation x where x.leader_id in (" + participants + ")))";
        List<AnalyticsFilterOptionsResponse.Option> courses = new ArrayList<>();
        for (Object[] r : repository.page("select c.course_code, c.course_name, c.course_type_code from training_course c where c.status = true and " + visible + " order by c.start_date desc, c.course_name", ctx.params, 0, OPTION_LIMIT))
            courses.add(option(r[0], r[1], r[2]));
        return AnalyticsFilterOptionsResponse.builder().deaneries(deaneries).parishes(parishes).courseTypes(types).courses(courses).leaderLevels(levels).build();
    }

    private static AnalyticsFilterOptionsResponse.Option option(Object value, Object label, Object parent) {
        return AnalyticsFilterOptionsResponse.Option.builder().value(String.valueOf(value)).label(String.valueOf(label)).parent(parent == null ? null : String.valueOf(parent)).build();
    }
}
