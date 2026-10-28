package phucuong.ht.modules.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.search.model.QuickSearchGroup;
import phucuong.ht.modules.search.model.QuickSearchItem;
import phucuong.ht.modules.search.repository.SearchRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tìm kiếm nhanh bằng PostgreSQL (unaccent + pg_trgm). Mỗi nhóm kết quả chỉ chạy khi tài khoản có quyền đọc tương ứng
 * và luôn bị giới hạn theo phạm vi của tài khoản; phạm vi không bao giờ lấy từ client.
 */
@Service
@RequiredArgsConstructor
public class SearchService {
    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_LIMIT = 10;

    private enum Level { ALL, DIOCESE, DEANERY, PARISH }

    private record Scope(Level level, UUID id) {
    }

    private final SearchRepository searchRepository;
    private final AccountRepository accountRepository;
    private final securityUtils securityUtils;

    public List<QuickSearchGroup> quick(String rawQuery, int rawLimit, Authentication authentication) {
        String q = SearchTextNormalizer.normalize(rawQuery);
        if (q.length() < MIN_QUERY_LENGTH) return List.of();
        if (q.length() > MAX_QUERY_LENGTH) q = q.substring(0, MAX_QUERY_LENGTH);
        int limit = Math.max(1, Math.min(rawLimit, MAX_LIMIT));
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) return List.of();

        Map<String, Object> base = new HashMap<>();
        base.put("q", q);
        base.put("pattern", "%" + SearchTextNormalizer.escapeLike(q) + "%");
        base.put("prefix", SearchTextNormalizer.escapeLike(q) + "%");

        List<QuickSearchGroup> groups = new ArrayList<>();
        add(groups, "leader", "Huynh trưởng", scope(authentication, username, "organization.leader", Level.DIOCESE, Level.DEANERY, Level.PARISH),
                SearchService::leaderSql, base, limit);
        add(groups, "diocese", "Giáo phận", scope(authentication, username, "organization.diocese", Level.ALL, Level.DIOCESE),
                SearchService::dioceseSql, base, limit);
        add(groups, "deanery", "Giáo hạt", scope(authentication, username, "organization.deanery", Level.ALL, Level.DIOCESE, Level.DEANERY),
                SearchService::deanerySql, base, limit);
        add(groups, "parish", "Giáo xứ", scope(authentication, username, "organization.parish", Level.ALL, Level.DIOCESE, Level.DEANERY, Level.PARISH),
                SearchService::parishSql, base, limit);
        add(groups, "course", "Khóa huấn luyện", scope(authentication, username, "training.course", Level.DIOCESE, Level.DEANERY),
                SearchService::courseSql, base, limit);
        add(groups, "certificate", "Chứng chỉ", scope(authentication, username, "training.certificate", Level.DIOCESE, Level.DEANERY),
                SearchService::certificateSql, base, limit);
        return groups;
    }

    private void add(List<QuickSearchGroup> groups, String type, String label, Optional<Scope> scope,
                     Function<Scope, String> sql, Map<String, Object> base, int limit) {
        if (scope.isEmpty()) return;
        Map<String, Object> params = new HashMap<>(base);
        if (scope.get().level() != Level.ALL) params.put("scopeId", scope.get().id());
        List<QuickSearchItem> items = searchRepository.rows(sql.apply(scope.get()), params, limit).stream()
                .map(row -> new QuickSearchItem((String) row[0], (String) row[1], (String) row[2], (Boolean) row[3]))
                .toList();
        if (!items.isEmpty()) groups.add(new QuickSearchGroup(type, label, items));
    }

    /** Lấy phạm vi rộng nhất mà tài khoản có quyền đọc; không xác định được đơn vị đang hoạt động thì bỏ nhóm. */
    private Optional<Scope> scope(Authentication authentication, String username, String permission, Level... levels) {
        for (Level level : levels) {
            if (!securityUtils.hasAuthority(authentication, permission + ".read." + level.name().toLowerCase())) continue;
            Optional<UUID> id = switch (level) {
                case ALL -> Optional.of(new UUID(0, 0));
                case DIOCESE -> accountRepository.findActiveDioceseIdByUsername(username);
                case DEANERY -> accountRepository.findActiveDeaneryIdByUsername(username);
                case PARISH -> accountRepository.findActiveParishIdByUsername(username);
            };
            if (id.isPresent()) return Optional.of(new Scope(level, id.get()));
        }
        return Optional.empty();
    }

    // SQL: expr đã bỏ dấu/chữ thường bằng immutable_unaccent để khớp index GIN trigram (V109).

    private static String match(String... exprs) {
        List<String> parts = new ArrayList<>();
        for (String expr : exprs) parts.add(expr + " like :pattern or cast(:q as text) <% " + expr);
        return "(" + String.join(" or ", parts) + ")";
    }

    private static String rank(String expr) {
        return "(case when " + expr + " like :prefix then 2 when " + expr + " like :pattern then 1 else 0 end) + word_similarity(cast(:q as text), " + expr + ")";
    }

    private static String leaderSql(Scope s) {
        String name = "immutable_unaccent(l.holy_name || ' ' || l.last_name || ' ' || l.first_name)";
        String email = "immutable_unaccent(l.email)";
        String scope = switch (s.level()) {
            case DIOCESE -> " and de.diocese_id = :scopeId";
            case DEANERY -> " and p.deanery_id = :scopeId";
            case PARISH -> " and p.id = :scopeId";
            default -> "";
        };
        return "select cast(l.id as text), trim(l.holy_name || ' ' || l.last_name || ' ' || l.first_name), concat_ws(' - ', p.name, de.name), l.status "
                + "from leader l join parish p on p.id = l.parish_id join deanery de on de.id = p.deanery_id "
                + "where (" + match(name, email) + " or l.phone_number like :pattern)" + scope
                + " order by " + rank(name) + " desc, l.last_name, l.first_name";
    }

    private static String dioceseSql(Scope s) {
        String name = "immutable_unaccent(d.name)";
        return "select cast(d.id as text), d.name, d.union_name, d.status from diocese d where " + match(name)
                + (s.level() == Level.DIOCESE ? " and d.id = :scopeId" : "") + " order by " + rank(name) + " desc, d.name";
    }

    private static String deanerySql(Scope s) {
        String name = "immutable_unaccent(de.name)";
        String scope = switch (s.level()) {
            case DIOCESE -> " and de.diocese_id = :scopeId";
            case DEANERY -> " and de.id = :scopeId";
            default -> "";
        };
        return "select cast(de.id as text), de.name, d.name, de.status from deanery de join diocese d on d.id = de.diocese_id where "
                + match(name) + scope + " order by " + rank(name) + " desc, de.name";
    }

    private static String parishSql(Scope s) {
        String name = "immutable_unaccent(p.name)";
        String scope = switch (s.level()) {
            case DIOCESE -> " and de.diocese_id = :scopeId";
            case DEANERY -> " and p.deanery_id = :scopeId";
            case PARISH -> " and p.id = :scopeId";
            default -> "";
        };
        return "select cast(p.id as text), p.name, concat_ws(' - ', de.name, d.name), p.status "
                + "from parish p join deanery de on de.id = p.deanery_id join diocese d on d.id = de.diocese_id where "
                + match(name) + scope + " order by " + rank(name) + " desc, p.name";
    }

    private static String courseSql(Scope s) {
        String name = "immutable_unaccent(c.course_name)";
        String code = "immutable_unaccent(c.course_code)";
        String scope = s.level() == Level.DIOCESE ? " and c.diocese_id = :scopeId" : " and c.deanery_id = :scopeId";
        return "select c.course_code, c.course_name, concat_ws(' - ', c.course_code, c.location), c.status from training_course c where "
                + match(name, code) + scope + " order by " + rank(name) + " desc, c.start_date desc";
    }

    private static String certificateSql(Scope s) {
        String code = "immutable_unaccent(ce.certificate_code)";
        String scope = s.level() == Level.DIOCESE ? " and de.diocese_id = :scopeId" : " and p.deanery_id = :scopeId";
        return "select ce.certificate_code, ce.certificate_code, concat_ws(' - ', ce.certificate_name, trim(l.last_name || ' ' || l.first_name)), ce.status "
                + "from certificate ce join leader l on l.id = ce.leader_id join parish p on p.id = l.parish_id join deanery de on de.id = p.deanery_id where "
                + match(code) + scope + " order by " + rank(code) + " desc, ce.certificate_code";
    }
}
