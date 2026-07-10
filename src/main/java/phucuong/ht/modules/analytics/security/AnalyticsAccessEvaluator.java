package phucuong.ht.modules.analytics.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;

@Component("analyticsAccessEvaluator") @RequiredArgsConstructor
public class AnalyticsAccessEvaluator {
    public static final String READ_ALL = "analytics.dashboard.read.all";
    public static final String READ_DIOCESE = "analytics.dashboard.read.diocese";
    public static final String READ_DEANERY = "analytics.dashboard.read.deanery";
    public static final String READ_PARISH = "analytics.dashboard.read.parish";
    private final securityUtils securityUtils;
    public boolean canReadOverview(Authentication authentication) { return hasScoped(authentication) || securityUtils.hasAuthority(authentication, READ_ALL); }
    public boolean canReadDetailed(Authentication authentication) { return hasScoped(authentication); }
    private boolean hasScoped(Authentication authentication) { return securityUtils.hasAuthority(authentication, READ_DIOCESE) || securityUtils.hasAuthority(authentication, READ_DEANERY) || securityUtils.hasAuthority(authentication, READ_PARISH); }
}
