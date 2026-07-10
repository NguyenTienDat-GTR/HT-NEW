package phucuong.ht.modules.analytics.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.auth.repository.AccountRepository;

@Component
@RequiredArgsConstructor
public class AnalyticsScopeResolver {
    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    public AnalyticsScope resolve(Authentication authentication, boolean allowSystemOverview) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) throw new AnalyticsAccessDeniedException("Không xác định được tài khoản hiện tại");
        if (securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_DIOCESE)) {
            var id = accountRepository.findActiveDioceseIdByUsername(username);
            if (id.isPresent()) return new AnalyticsScope(AnalyticsScopeType.DIOCESE, id.get(), null, null);
        }
        if (securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_DEANERY)) {
            var id = accountRepository.findActiveDeaneryIdByUsername(username);
            if (id.isPresent()) return new AnalyticsScope(AnalyticsScopeType.DEANERY, null, id.get(), null);
        }
        if (securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_PARISH)) {
            var id = accountRepository.findActiveParishIdByUsername(username);
            if (id.isPresent()) return new AnalyticsScope(AnalyticsScopeType.PARISH, null, null, id.get());
        }
        if (allowSystemOverview && securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_ALL)) return new AnalyticsScope(AnalyticsScopeType.ALL, null, null, null);
        throw new AnalyticsAccessDeniedException("Tài khoản không có phạm vi thống kê hợp lệ");
    }
}
