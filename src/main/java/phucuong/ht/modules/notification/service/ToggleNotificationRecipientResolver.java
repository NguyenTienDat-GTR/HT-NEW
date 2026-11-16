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

    /** Người giữ vai trò thuộc đơn vị {@code unitId} (giáo phận, giáo hạt hoặc giáo xứ) hoặc đơn vị cấp dưới của nó. */
    public Set<String> byRoleInUnit(String roleCode, UUID unitId) {
        if (roleCode == null || roleCode.isBlank() || unitId == null) {
            return Set.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                select distinct ar.username
                from account_role ar
                join account a on a.username = ar.username
                join v_account_unit au on au.username = ar.username
                where ar.role_code = :roleCode
                  and ar.status = true
                  and a.status = true
                  and (au.diocese_id = :unitId or au.deanery_id = :unitId or au.parish_id = :unitId)
                """)
                .setParameter("roleCode", roleCode)
                .setParameter("unitId", unitId)
                .getResultList();
        return unitOperationalStatusService.normalize(rows.stream().map(String::valueOf).toList());
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
                      and rp.revoked_at is null
                    union
                    select ar.username
                    from account_role ar
                    join role_permission_unit_override o on o.role_code = ar.role_code
                    where o.permission_code = :code
                      and ar.status = true
                      and o.override_type = 'GRANT'
                      and o.status = true
                      and o.revoked_at is null
                    union
                    select apo.username
                    from account_permission_override apo
                    where apo.permission_code = :code
                      and apo.status = true
                      and apo.revoked_at is null
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
