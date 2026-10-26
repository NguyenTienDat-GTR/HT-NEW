package phucuong.ht.modules.analytics.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Việc cần xử lý, khóa nổi bật và KPI chỉ tính khóa thuộc phạm vi của tài khoản. */
@SpringBootTest
class OverviewCourseScopeIntegrationTest {

    @Autowired
    private OverviewAnalytics overview;

    @Test
    void deaneryScopeShouldOnlyCountCoursesOrganizedByThatDeanery() {
        AnalyticsContext ctx = new AnalyticsContext(
                new AnalyticsScope(AnalyticsScopeType.DEANERY, null, UUID.randomUUID(), null), new AnalyticsFilterRequest());

        String filters = ctx.courseFilters();

        assertTrue(filters.contains("c.deanery_id = :deaneryId"), filters);
        assertFalse(filters.contains("c.diocese_id = :dioceseId"), "Giáo hạt không đếm khóa do giáo phận tổ chức: " + filters);
    }

    @Test
    void dioceseScopeShouldCoverDioceseCoursesAndItsDeaneryCourses() {
        AnalyticsContext ctx = new AnalyticsContext(
                new AnalyticsScope(AnalyticsScopeType.DIOCESE, UUID.randomUUID(), null, null), new AnalyticsFilterRequest());

        String filters = ctx.courseFilters();

        assertTrue(filters.contains("c.diocese_id = :dioceseId"), filters);
        assertTrue(filters.contains("de.diocese_id = :dioceseId"), filters);
    }

    @Test
    void parishScopeShouldNotRestrictByOrganizer() {
        AnalyticsContext ctx = new AnalyticsContext(
                new AnalyticsScope(AnalyticsScopeType.PARISH, null, null, UUID.randomUUID()), new AnalyticsFilterRequest());

        assertEquals("", ctx.courseFilters());
    }

    @Test
    void clientCourseFiltersShouldNeverWidenTheScope() {
        AnalyticsFilterRequest filters = new AnalyticsFilterRequest();
        filters.setCourseCode("KHOA-CUA-GIAO-PHAN");
        AnalyticsContext ctx = new AnalyticsContext(
                new AnalyticsScope(AnalyticsScopeType.DEANERY, null, UUID.randomUUID(), null), filters);

        String sql = ctx.courseFilters();

        assertTrue(sql.indexOf("c.deanery_id = :deaneryId") < sql.indexOf("c.course_code = :fCourse"), sql);
    }

    @Test
    void workQueueAndOpenCoursesQueriesShouldRunAgainstTheRealSchemaForEveryOrganizerScope() {
        for (AnalyticsScopeType type : new AnalyticsScopeType[]{AnalyticsScopeType.DIOCESE, AnalyticsScopeType.DEANERY}) {
            UUID id = UUID.randomUUID();
            AnalyticsScope scope = type == AnalyticsScopeType.DIOCESE
                    ? new AnalyticsScope(type, id, null, null) : new AnalyticsScope(type, null, id, null);
            AnalyticsContext ctx = new AnalyticsContext(scope, new AnalyticsFilterRequest());

            assertEquals(5, overview.workQueue(ctx).size());
            overview.openCourses(ctx);
            overview.alerts(ctx);
            overview.overview(ctx);
        }
    }
}
