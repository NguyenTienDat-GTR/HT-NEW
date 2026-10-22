package phucuong.ht.modules.analytics.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.analytics.model.AnalyticsScope;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Phạm vi dữ liệu luôn lấy từ tài khoản và quyền, không nhận ID từ client. Tài khoản có nhiều quyền phạm vi
 * (giáo phận, giáo hạt, giáo xứ) được chọn xem phạm vi nào qua tham số `scope`, nhưng chỉ trong số phạm vi họ thực sự có quyền.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsScopeResolver {
    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    public AnalyticsScope resolve(Authentication authentication, boolean allowSystemOverview) {
        return resolve(authentication, allowSystemOverview, null);
    }

    /** preferred (diocese | deanery | parish) rỗng thì dùng phạm vi rộng nhất mà tài khoản có quyền; có giá trị mà không có quyền thì từ chối. */
    public AnalyticsScope resolve(Authentication authentication, boolean allowSystemOverview, String preferred) {
        String username = username(authentication);
        AnalyticsScopeType wanted = parse(preferred);
        if (wanted == null) {
            for (AnalyticsScopeType type : new AnalyticsScopeType[]{AnalyticsScopeType.DIOCESE, AnalyticsScopeType.DEANERY, AnalyticsScopeType.PARISH}) {
                Optional<AnalyticsScope> scope = scopeOf(authentication, username, type);
                if (scope.isPresent()) return scope.get();
            }
        } else {
            Optional<AnalyticsScope> scope = scopeOf(authentication, username, wanted);
            if (scope.isPresent()) return scope.get();
            throw new AnalyticsAccessDeniedException("Tài khoản không có quyền xem thống kê ở phạm vi đã chọn");
        }
        if (allowSystemOverview && securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_ALL)) return new AnalyticsScope(AnalyticsScopeType.ALL, null, null, null);
        throw new AnalyticsAccessDeniedException("Tài khoản không có phạm vi thống kê hợp lệ");
    }

    /** Mọi phạm vi tài khoản được xem, từ rộng tới hẹp, để FE hiện bộ chọn phạm vi khi có từ hai phạm vi trở lên. */
    public List<AnalyticsScope> available(Authentication authentication) {
        String username = username(authentication);
        List<AnalyticsScope> result = new ArrayList<>();
        for (AnalyticsScopeType type : new AnalyticsScopeType[]{AnalyticsScopeType.DIOCESE, AnalyticsScopeType.DEANERY, AnalyticsScopeType.PARISH})
            scopeOf(authentication, username, type).ifPresent(result::add);
        return result;
    }

    private String username(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) throw new AnalyticsAccessDeniedException("Không xác định được tài khoản hiện tại");
        return username;
    }

    private AnalyticsScopeType parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            AnalyticsScopeType type = AnalyticsScopeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (type == AnalyticsScopeType.ALL) throw new IllegalArgumentException();
            return type;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scope chỉ nhận diocese, deanery hoặc parish");
        }
    }

    private Optional<AnalyticsScope> scopeOf(Authentication authentication, String username, AnalyticsScopeType type) {
        return switch (type) {
            case DIOCESE -> securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_DIOCESE)
                    ? accountRepository.findActiveDioceseIdByUsername(username).map(id -> new AnalyticsScope(AnalyticsScopeType.DIOCESE, id, null, null)) : Optional.empty();
            case DEANERY -> securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_DEANERY)
                    ? accountRepository.findActiveDeaneryIdByUsername(username).map(id -> new AnalyticsScope(AnalyticsScopeType.DEANERY, null, id, null)) : Optional.empty();
            case PARISH -> securityUtils.hasAuthority(authentication, AnalyticsAccessEvaluator.READ_PARISH)
                    ? accountRepository.findActiveParishIdByUsername(username).map(id -> new AnalyticsScope(AnalyticsScopeType.PARISH, null, null, id)) : Optional.empty();
            case ALL -> Optional.empty();
        };
    }
}
