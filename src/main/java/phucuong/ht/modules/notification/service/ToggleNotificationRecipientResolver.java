package phucuong.ht.modules.notification.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.security.UnitOperationalStatusService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ToggleNotificationRecipientResolver {

    @PersistenceContext
    private EntityManager entityManager;

    private final UnitOperationalStatusService unitOperationalStatusService;

    public ToggleNotificationRecipientResolver(UnitOperationalStatusService unitOperationalStatusService) {
        this.unitOperationalStatusService = unitOperationalStatusService;
    }

    public Set<String> byRole(String roleCode) {
        return activeUsernames("""
                select distinct ar.username
                from account_role ar
                join account a on a.username = ar.username
                where ar.role_code = :code
                  and ar.status = true
                  and a.status = true
                """, roleCode);
    }

    public Set<String> byPermission(String permissionCode) {
        return activeUsernames("""
                select distinct affected.username
                from (
                    select ar.username
                    from account_role ar
                    join role_permission rp on rp.role_code = ar.role_code
                    where rp.permission_code = :code
                      and ar.status = true
                      and rp.status = true
                    union
                    select apo.username
                    from account_permission_override apo
                    where apo.permission_code = :code
                      and apo.status = true
                ) affected
                join account a on a.username = affected.username
                where a.status = true
                """, permissionCode);
    }

    public Set<String> byAccount(String username) {
        return activeUsernames("""
                select a.username
                from account a
                where a.username = :code
                  and a.status = true
                """, username);
    }

    public Set<String> byUnit(UnitType unitType, UUID unitId) {
        return unitOperationalStatusService.findActiveUsernamesInSubtree(unitType, unitId);
    }

    private Set<String> activeUsernames(String sql, String code) {
        if (code == null || code.isBlank()) {
            return Set.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(sql)
                .setParameter("code", code)
                .getResultList();
        return unitOperationalStatusService.normalize(rows.stream().map(String::valueOf).toList());
    }
}
