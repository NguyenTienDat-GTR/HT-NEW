package phucuong.ht.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.PeriodRange;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;
import phucuong.ht.modules.analytics.model.request.AnalyticsRecordRequest;
import phucuong.ht.modules.analytics.model.response.AnalyticsFilterOptionsResponse;
import phucuong.ht.modules.analytics.model.response.AnalyticsRecordsResponse;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.model.response.AnalyticsScopeOptionResponse;
import phucuong.ht.modules.analytics.model.response.OpenCourseResponse;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;
import phucuong.ht.modules.analytics.model.response.WorkQueueItemResponse;
import phucuong.ht.modules.analytics.security.AnalyticsScopeResolver;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;

import java.util.List;
import java.util.Map;

/**
 * Điểm vào của analytics: xác định phạm vi quyền từ tài khoản (không nhận ID phạm vi từ client), chuẩn hóa bộ lọc,
 * giao cho từng nhóm phân tích, rồi gắn phạm vi và bộ lọc thực tế vào response.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final AnalyticsScopeResolver scopeResolver;
    private final OverviewAnalytics overview;
    private final PeopleAnalytics people;
    private final TrainingAnalytics training;
    private final RecordsAnalytics records;
    private final AnalyticsRepository repository;
    private final securityUtils securityUtils;

    @Cacheable(cacheNames = CacheNames.ANALYTICS_OVERVIEW, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'overview', #request.cacheKey())")
    public AnalyticsResponse overview(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, true, filters.getScope());
        AnalyticsContext ctx = new AnalyticsContext(scope, filters);
        if (scope.isSystemOverview()) return decorate(overview.systemOverview(filters.isIncludeInactiveUnits()), ctx);
        if (scope.type() == AnalyticsScopeType.PARISH) return decorate(overview.parishOverview(ctx), ctx);
        return decorate(overview.overview(ctx), ctx);
    }

    public List<WorkQueueItemResponse> workQueue(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, true, filters.getScope());
        if (scope.isSystemOverview()) return List.of();
        return overview.workQueue(new AnalyticsContext(scope, filters));
    }

    public List<OpenCourseResponse> openCourses(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        AnalyticsScope scope = scopeResolver.resolve(authentication, true, filters.getScope());
        if (scope.isSystemOverview() || !canRegisterAt(authentication, scope)) return List.of();
        return overview.openCourses(new AnalyticsContext(scope, filters));
    }

    /** Giáo hạt và giáo phận chỉ thấy khóa đang mở đăng ký khi tài khoản có quyền gửi đăng ký của đúng cấp đó; giáo xứ luôn thấy. */
    private boolean canRegisterAt(Authentication authentication, AnalyticsScope scope) {
        return switch (scope.type()) {
            case DIOCESE -> securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE);
            case DEANERY -> securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
            default -> true;
        };
    }

    /** Các phạm vi tài khoản được xem (kèm tên đơn vị) để FE hiện bộ chọn khi có từ hai phạm vi trở lên. */
    public List<AnalyticsScopeOptionResponse> scopes(Authentication authentication) {
        List<AnalyticsScopeOptionResponse> result = new java.util.ArrayList<>();
        for (AnalyticsScope scope : scopeResolver.available(authentication)) {
            String sql = switch (scope.type()) {
                case DIOCESE -> "select name from diocese where id = :dioceseId";
                case DEANERY -> "select name from deanery where id = :deaneryId";
                default -> "select name from parish where id = :parishId";
            };
            Map<String, Object> params = new java.util.HashMap<>();
            if (scope.dioceseId() != null) params.put("dioceseId", scope.dioceseId());
            if (scope.deaneryId() != null) params.put("deaneryId", scope.deaneryId());
            if (scope.parishId() != null) params.put("parishId", scope.parishId());
            Object[] row = repository.row(sql, params);
            result.add(AnalyticsScopeOptionResponse.builder().type(scope.type().name().toLowerCase()).name(String.valueOf(row[0])).build());
        }
        return result;
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_ORGANIZATION, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'organization', #request.cacheKey())")
    public AnalyticsResponse organization(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsContext ctx = detailed(request, authentication);
        return decorate(people.organization(ctx), ctx);
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_LEADERS, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'leaders', #request.cacheKey())")
    public AnalyticsResponse leaders(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsContext ctx = detailed(request, authentication);
        return decorate(people.leaders(ctx), ctx);
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_COURSES, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'courses', #request.cacheKey())")
    public AnalyticsResponse courses(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsContext ctx = detailed(request, authentication);
        return decorate(training.courses(ctx), ctx);
    }

    @Cacheable(cacheNames = CacheNames.ANALYTICS_CERTIFICATES, key = "@cacheScopeKeyHelper.analyticsKey(#authentication, 'certificates', #request.cacheKey())")
    public AnalyticsResponse certificates(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsContext ctx = detailed(request, authentication);
        return decorate(training.certificates(ctx), ctx);
    }

    public AnalyticsFilterOptionsResponse filterOptions(AnalyticsFilterRequest request, Authentication authentication) {
        return records.options(detailed(request, authentication));
    }

    public AnalyticsRecordsResponse records(AnalyticsRecordRequest request, Authentication authentication) {
        AnalyticsRecordRequest source = request == null ? new AnalyticsRecordRequest() : request;
        AnalyticsContext ctx = detailed(source, authentication);
        return records.records(ctx, source, source.getPage() * source.getSize(), source.getSize());
    }

    public AnalyticsRecordsResponse export(AnalyticsRecordRequest request, Authentication authentication) {
        AnalyticsRecordRequest source = request == null ? new AnalyticsRecordRequest() : request;
        AnalyticsContext ctx = detailed(source, authentication);
        return records.records(ctx, source, 0, AnalyticsRecordRequest.MAX_EXPORT_ROWS);
    }

    private AnalyticsContext detailed(AnalyticsFilterRequest request, Authentication authentication) {
        AnalyticsFilterRequest filters = validate(request);
        return new AnalyticsContext(scopeResolver.resolve(authentication, false, filters.getScope()), filters);
    }

    private AnalyticsFilterRequest validate(AnalyticsFilterRequest request) {
        AnalyticsFilterRequest result = request == null ? new AnalyticsFilterRequest() : request;
        result.validate();
        return result;
    }

    private AnalyticsResponse decorate(AnalyticsResponse body, AnalyticsContext ctx) {
        PeriodRange previous = ctx.range.previous();
        boolean compare = ctx.filters.isCompare();
        return body.toBuilder().scope(ctx.scope.type().name().toLowerCase())
                .filters(AnalyticsResponse.Filters.builder().fromDate(ctx.range.from()).toDate(ctx.range.to())
                        .compareFromDate(compare ? previous.from() : null).compareToDate(compare ? previous.to() : null)
                        .timeBucket(ctx.filters.resolvedTimeBucket().name().toLowerCase()).groupBy(ctx.filters.getGroupBy())
                        .includeInactiveUnits(ctx.filters.isIncludeInactiveUnits()).build())
                .build();
    }
}
