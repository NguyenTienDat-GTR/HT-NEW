package phucuong.ht.modules.analytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;
import phucuong.ht.modules.analytics.model.response.OpenCourseResponse;
import phucuong.ht.modules.analytics.repository.AnalyticsRepository;
import phucuong.ht.modules.analytics.security.AnalyticsAccessEvaluator;
import phucuong.ht.modules.analytics.security.AnalyticsScopeResolver;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Khóa đang mở đăng ký ở tổng quan: giáo hạt và giáo phận chỉ thấy khi tài khoản có quyền gửi đăng ký của đúng cấp đó. */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceOpenCoursesTest {
    @Mock private AnalyticsScopeResolver scopeResolver;
    @Mock private OverviewAnalytics overview;
    @Mock private PeopleAnalytics people;
    @Mock private TrainingAnalytics training;
    @Mock private RecordsAnalytics records;
    @Mock private AnalyticsRepository repository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(scopeResolver, overview, people, training, records, repository, new securityUtils(), null);
    }

    @Test
    void deaneryWithSubmitPermissionShouldSeeOpenCourses() {
        var auth = auth(AnalyticsAccessEvaluator.READ_DEANERY, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
        when(scopeResolver.resolve(any(), anyBoolean(), any())).thenReturn(new AnalyticsScope(AnalyticsScopeType.DEANERY, null, UUID.randomUUID(), null));
        OpenCourseResponse course = OpenCourseResponse.builder().courseCode("K1").courseName("Khóa 1").build();
        when(overview.openCourses(any())).thenReturn(List.of(course));

        assertEquals(List.of(course), service.openCourses(new AnalyticsFilterRequest(), auth));
    }

    @Test
    void deaneryWithoutSubmitPermissionShouldSeeNothing() {
        var auth = auth(AnalyticsAccessEvaluator.READ_DEANERY);
        when(scopeResolver.resolve(any(), anyBoolean(), any())).thenReturn(new AnalyticsScope(AnalyticsScopeType.DEANERY, null, UUID.randomUUID(), null));

        assertTrue(service.openCourses(new AnalyticsFilterRequest(), auth).isEmpty());
        verify(overview, never()).openCourses(any());
    }

    @Test
    void dioceseNeedsTheDioceseSubmitPermissionNotTheDeaneryOne() {
        var auth = auth(AnalyticsAccessEvaluator.READ_DIOCESE, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
        when(scopeResolver.resolve(any(), anyBoolean(), any())).thenReturn(new AnalyticsScope(AnalyticsScopeType.DIOCESE, UUID.randomUUID(), null, null));

        assertTrue(service.openCourses(new AnalyticsFilterRequest(), auth).isEmpty());
        verify(overview, never()).openCourses(any());
    }

    @Test
    void dioceseWithSubmitPermissionShouldSeeOpenCourses() {
        var auth = auth(AnalyticsAccessEvaluator.READ_DIOCESE, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE);
        when(scopeResolver.resolve(any(), anyBoolean(), any())).thenReturn(new AnalyticsScope(AnalyticsScopeType.DIOCESE, UUID.randomUUID(), null, null));
        OpenCourseResponse course = OpenCourseResponse.builder().courseCode("K2").courseName("Khóa 2").build();
        when(overview.openCourses(any())).thenReturn(List.of(course));

        assertEquals(List.of(course), service.openCourses(new AnalyticsFilterRequest(), auth));
    }

    @Test
    void parishKeepsSeeingOpenCoursesWithoutExtraPermission() {
        var auth = auth(AnalyticsAccessEvaluator.READ_PARISH);
        when(scopeResolver.resolve(any(), anyBoolean(), any())).thenReturn(new AnalyticsScope(AnalyticsScopeType.PARISH, null, null, UUID.randomUUID()));
        when(overview.openCourses(any())).thenReturn(List.of());

        assertTrue(service.openCourses(new AnalyticsFilterRequest(), auth).isEmpty());
        verify(overview).openCourses(any());
    }

    private UsernamePasswordAuthenticationToken auth(String... authorities) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("u", "leader", List.of(), List.of(authorities));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
