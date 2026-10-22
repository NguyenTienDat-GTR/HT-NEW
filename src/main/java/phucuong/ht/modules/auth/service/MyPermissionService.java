package phucuong.ht.modules.auth.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.response.MyPermissionResponse;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Quyền đang có hiệu lực của chính tài khoản đang đăng nhập (chỉ đọc). */
@Service
@RequiredArgsConstructor
public class MyPermissionService {

    private final securityUtils securityUtils;
    private final CommonValidationHelper commonValidationHelper;
    private final AuditActorNameResolver auditActorNameResolver;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Set<String> SORT_FIELDS = Set.of("permissionName", "moduleName", "resourceName", "actionName",
            "scopeName", "sourceType", "assignedAt", "expiresAt");

    /** Phân trang, tìm kiếm, lọc (sourceType, moduleName, actionName) và sắp xếp ở BE. */
    @Transactional(readOnly = true)
    public Page<MyPermissionResponse> search(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest criteria = request == null ? new BaseSearchRequest() : request;
        String keyword = criteria.getSearch() == null ? "" : criteria.getSearch().trim().toLowerCase(Locale.ROOT);
        String source = filter(criteria, "sourceType");
        String module = filter(criteria, "moduleName");
        String action = filter(criteria, "actionName");

        List<MyPermissionResponse> filtered = currentPermissions(authentication).stream()
                .filter(item -> source == null || source.equalsIgnoreCase(item.getSourceType()))
                .filter(item -> module == null || module.equals(item.getModuleName()))
                .filter(item -> action == null || action.equals(item.getActionName()))
                .filter(item -> keyword.isEmpty() || Stream.of(item.getPermissionName(), item.getDescription(), item.getModuleName(),
                                item.getResourceName(), item.getActionName(), item.getRoleName())
                        .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(keyword)))
                .sorted(comparator(criteria))
                .toList();

        int from = Math.min(criteria.getPage() * criteria.getSize(), filtered.size());
        int to = Math.min(from + criteria.getSize(), filtered.size());
        return new PageImpl<>(filtered.subList(from, to), PageRequest.of(criteria.getPage(), criteria.getSize()), filtered.size());
    }

    /** Giá trị cho các ô lọc phân hệ / hành động của trang. */
    @Transactional(readOnly = true)
    public MyPermissionFilterOptions filterOptions(Authentication authentication) {
        List<MyPermissionResponse> all = currentPermissions(authentication);
        return new MyPermissionFilterOptions(
                all.stream().map(MyPermissionResponse::getModuleName).filter(Objects::nonNull).distinct().sorted().toList(),
                all.stream().map(MyPermissionResponse::getActionName).filter(Objects::nonNull).distinct().sorted().toList());
    }

    public record MyPermissionFilterOptions(List<String> modules, List<String> actions) {
    }

    private String filter(BaseSearchRequest criteria, String key) {
        Object value = criteria.getFilters().get(key);
        if (value == null || String.valueOf(value).isBlank() || "all".equals(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value);
    }

    private Comparator<MyPermissionResponse> comparator(BaseSearchRequest criteria) {
        String field = SORT_FIELDS.contains(criteria.getSortBy()) ? criteria.getSortBy() : "permissionName";
        Comparator<MyPermissionResponse> comparator = Comparator.comparing(
                (MyPermissionResponse item) -> sortKey(item, field), Comparator.nullsLast(Comparator.naturalOrder()));
        if ("DESC".equalsIgnoreCase(criteria.getSortDirection())) {
            comparator = Comparator.comparing((MyPermissionResponse item) -> sortKey(item, field),
                    Comparator.nullsLast(Comparator.<Comparable>reverseOrder()));
        }
        return comparator.thenComparing(MyPermissionResponse::getPermissionCode);
    }

    private Comparable sortKey(MyPermissionResponse item, String field) {
        return switch (field) {
            case "moduleName" -> lower(item.getModuleName());
            case "resourceName" -> lower(item.getResourceName());
            case "actionName" -> lower(item.getActionName());
            case "scopeName" -> lower(item.getScopeName());
            case "sourceType" -> item.getSourceType();
            case "assignedAt" -> item.getAssignedAt();
            case "expiresAt" -> item.getExpiresAt();
            default -> lower(item.getPermissionName());
        };
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.forLanguageTag("vi"));
    }

    private List<MyPermissionResponse> currentPermissions(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select p.permission_code, p.permission_name, p.description,
                       pm.module_name, pr.resource_name, pa.action_name, ps.scope_name,
                       v.source_type, v.role_code, r.role_name,
                       coalesce(rp.conditions, apo.reason) as note,
                       coalesce(rp.assigned_by, apo.created_by) as assigned_by,
                       coalesce(rp.assigned_at, apo.created_at) as assigned_at,
                       v.permission_expires_at
                from v_account_effective_permission v
                join permission p on p.permission_code = v.permission_code
                left join permission_module pm on pm.module_code = p.module_code
                left join permission_resource pr on pr.resource_code = p.resource_code and pr.module_code = p.module_code
                left join permission_action pa on pa.action_code = p.action_code
                left join permission_scope ps on ps.scope_code = p.scope_code
                left join role r on r.role_code = v.role_code
                left join role_permission rp on v.source_type = 'ROLE'
                     and rp.role_code = v.role_code and rp.permission_code = v.permission_code
                left join account_permission_override apo on v.source_type <> 'ROLE'
                     and apo.username = v.username and apo.permission_code = v.permission_code
                where v.username = :username
                order by pm.module_name, pr.resource_name, p.display_order, p.permission_code
                """)
                .setParameter("username", username)
                .getResultList();

        return rows.stream().map(row -> MyPermissionResponse.builder()
                .permissionCode(text(row[0]))
                .permissionName(text(row[1]))
                .description(text(row[2]))
                .moduleName(text(row[3]))
                .resourceName(text(row[4]))
                .actionName(text(row[5]))
                .scopeName(text(row[6]))
                .sourceType("ROLE".equals(text(row[7])) ? "ROLE" : "ACCOUNT")
                .roleCode(text(row[8]))
                .roleName(text(row[9]))
                .note(text(row[10]))
                .assignedByName(auditActorNameResolver.resolve(text(row[11])))
                .assignedAt(offset(row[12]))
                .expiresAt(offset(row[13]))
                .build()).toList();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private OffsetDateTime offset(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }
}
