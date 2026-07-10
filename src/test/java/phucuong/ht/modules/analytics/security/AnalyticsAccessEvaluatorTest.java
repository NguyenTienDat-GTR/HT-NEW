package phucuong.ht.modules.analytics.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import phucuong.ht.common.utils.securityUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsAccessEvaluatorTest {
    private final AnalyticsAccessEvaluator evaluator = new AnalyticsAccessEvaluator(new securityUtils());
    @Test void superAdminPermissionCanReadOnlyOverview() { var auth=auth(AnalyticsAccessEvaluator.READ_ALL); assertTrue(evaluator.canReadOverview(auth)); assertFalse(evaluator.canReadDetailed(auth)); }
    @Test void scopedPermissionsCanReadDetailedDashboard() { assertTrue(evaluator.canReadDetailed(auth(AnalyticsAccessEvaluator.READ_DIOCESE))); assertTrue(evaluator.canReadDetailed(auth(AnalyticsAccessEvaluator.READ_DEANERY))); assertTrue(evaluator.canReadDetailed(auth(AnalyticsAccessEvaluator.READ_PARISH))); }
    private UsernamePasswordAuthenticationToken auth(String authority) { return new UsernamePasswordAuthenticationToken("analytics-user",null,List.of(new SimpleGrantedAuthority(authority))); }
}
