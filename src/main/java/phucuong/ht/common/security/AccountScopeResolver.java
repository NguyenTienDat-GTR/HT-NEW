package phucuong.ht.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phạm vi dữ liệu của tài khoản theo một hành động. Tài khoản nhiều role có quyền ở nhiều cấp (giáo phận, giáo hạt, giáo xứ)
 * thì được dùng hợp của các phạm vi đó, thay vì chỉ nhánh đầu tiên khớp.
 */
@Component
@RequiredArgsConstructor
public class AccountScopeResolver {

    public enum Level {DIOCESE, DEANERY, PARISH}

    public record Scope(Level level, UUID unitId) {
    }

    public record ScopeSet(List<Scope> scopes) {

        public boolean isEmpty() {
            return scopes.isEmpty();
        }

        public boolean has(Level level) {
            return unitId(level) != null;
        }

        public UUID unitId(Level level) {
            return scopes.stream().filter(scope -> scope.level() == level).map(Scope::unitId).findFirst().orElse(null);
        }

        /** Mỗi phạm vi thành một nhóm điều kiện (field = unitId); các nhóm được nối bằng OR trong {@code BaseSearchRequest.scopeAnyOf}. */
        public List<Map<String, Object>> groups(Map<Level, String> fieldByLevel) {
            List<Map<String, Object>> groups = new ArrayList<>();
            for (Scope scope : scopes) {
                String field = fieldByLevel.get(scope.level());
                if (field != null) {
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put(field, scope.unitId());
                    groups.add(group);
                }
            }
            return groups;
        }
    }

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    /** {@code base} là mã quyền không kèm cấp, ví dụ "training.course.read" -> .diocese / .deanery / .parish. */
    public ScopeSet scopes(Authentication authentication, String base) {
        return scopes(authentication, base + ".diocese", base + ".deanery", base + ".parish");
    }

    /** Mã quyền null nghĩa là hành động không có cấp đó. Cấp mà tài khoản không có đơn vị hiệu lực thì bị bỏ qua. */
    public ScopeSet scopes(Authentication authentication, String dioceseAuthority, String deaneryAuthority, String parishAuthority) {
        return scopesAny(authentication, listOf(dioceseAuthority), listOf(deaneryAuthority), listOf(parishAuthority));
    }

    /** Như {@link #scopes}, nhưng mỗi cấp chỉ cần có một trong các quyền (ví dụ đọc, duyệt hoặc chấm điểm). */
    public ScopeSet scopesAny(Authentication authentication, List<String> dioceseAuthorities, List<String> deaneryAuthorities, List<String> parishAuthorities) {
        String username = securityUtils.currentUsername(authentication);
        List<Scope> result = new ArrayList<>();
        if (username == null || username.isBlank()) {
            return new ScopeSet(result);
        }
        if (hasAny(authentication, dioceseAuthorities)) {
            accountRepository.findActiveDioceseIdByUsername(username).ifPresent(id -> result.add(new Scope(Level.DIOCESE, id)));
        }
        if (hasAny(authentication, deaneryAuthorities)) {
            accountRepository.findActiveDeaneryIdByUsername(username).ifPresent(id -> result.add(new Scope(Level.DEANERY, id)));
        }
        if (hasAny(authentication, parishAuthorities)) {
            accountRepository.findActiveParishIdByUsername(username).ifPresent(id -> result.add(new Scope(Level.PARISH, id)));
        }
        return new ScopeSet(result);
    }

    /**
     * Cấp cao nhất mà tài khoản có quyền (giáo phận > giáo hạt > giáo xứ), theo hậu tố của các authority (".diocese", ".deanery", ".parish");
     * null nếu không có quyền theo cấp nào (vd. super admin). Cấp của tài khoản luôn lấy từ quyền, còn đơn vị lấy từ {@link #unitOf}:
     * không suy cấp từ việc tài khoản có gắn trực tiếp giáo phận / giáo hạt hay không, vì chỉ tài khoản giáo phận mới gắn trực tiếp,
     * các tài khoản khác gắn qua huynh trưởng.
     */
    public static Level levelOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Level highest = null;
        for (org.springframework.security.core.GrantedAuthority granted : authentication.getAuthorities()) {
            String authority = granted.getAuthority();
            if (authority == null) {
                continue;
            }
            Level level = authority.endsWith(".diocese") ? Level.DIOCESE : authority.endsWith(".deanery") ? Level.DEANERY : authority.endsWith(".parish") ? Level.PARISH : null;
            if (level != null && (highest == null || level.ordinal() < highest.ordinal())) {
                highest = level;
            }
        }
        return highest;
    }

    /** Đơn vị hiệu lực của tài khoản ở một cấp: gắn trực tiếp hoặc suy ra từ giáo xứ / huynh trưởng. */
    public UUID unitOf(String username, Level level) {
        if (username == null || username.isBlank() || level == null) {
            return null;
        }
        return switch (level) {
            case DIOCESE -> accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
            case DEANERY -> accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
            case PARISH -> accountRepository.findActiveParishIdByUsername(username).orElse(null);
        };
    }

    private boolean hasAny(Authentication authentication, List<String> authorities) {
        return authorities.stream().anyMatch(authority -> securityUtils.hasAuthority(authentication, authority));
    }

    private static List<String> listOf(String authority) {
        return authority == null ? List.of() : List.of(authority);
    }
}
