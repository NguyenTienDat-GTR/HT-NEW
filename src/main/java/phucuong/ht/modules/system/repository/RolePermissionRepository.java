package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.RolePermission;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RolePermissionRepository extends BaseRepo<RolePermission, String> {

    public RolePermissionRepository(EntityManager entityManager) {
        super(entityManager, RolePermission.class);
    }

    public Optional<RolePermission> findByRoleCodeAndPermissionCode(String roleCode, String permissionCode) {
        TypedQuery<RolePermission> query = entityManager.createQuery(
                """
                select rp
                from RolePermission rp
                join fetch rp.role
                join fetch rp.permission
                where rp.role.roleCode = :roleCode
                  and rp.permission.permissionCode = :permissionCode
                """,
                RolePermission.class
        );
        query.setParameter("roleCode", roleCode);
        query.setParameter("permissionCode", permissionCode);
        query.setMaxResults(1);
        List<RolePermission> results = query.getResultList();
        return results.stream().findFirst();
    }

    public List<String> findPermissionCodesByRoleCode(String roleCode) {
        TypedQuery<String> query = entityManager.createQuery(
                """
                select rp.permission.permissionCode
                from RolePermission rp
                where rp.role.roleCode = :roleCode
                """,
                String.class
        );
        query.setParameter("roleCode", roleCode);
        return query.getResultList();
    }

    public List<String> findActivePermissionCodesByAccountUsername(String username) {
        TypedQuery<String> query = entityManager.createQuery(
                """
                select distinct rp.permission.permissionCode
                from AccountRole ar
                join ar.role role
                join RolePermission rp on rp.role = role
                join rp.permission permission
                where ar.account.username = :username
                  and ar.status = true
                  and role.status = true
                  and rp.status = true
                  and permission.status = true
                  and (ar.expiresAt is null or ar.expiresAt > CURRENT_TIMESTAMP)
                  and (rp.expiresAt is null or rp.expiresAt > CURRENT_TIMESTAMP)
                """,
                String.class
        );
        query.setParameter("username", username);
        return query.getResultList();
    }

    public int deactivateExpiredAssignments(OffsetDateTime expiresAtThreshold, OffsetDateTime updatedAt, String actor) {
        return 0;
    }

    @Transactional
    public int updateStatusByRoleCode(String roleCode, boolean status, OffsetDateTime updatedAt, String actor) {
        return entityManager.createQuery(
                        """
                        update RolePermission rp
                        set rp.status = :status,
                            rp.updatedAt = :updatedAt,
                            rp.updatedBy = :actor
                        where rp.role.roleCode = :roleCode
                        """
                )
                .setParameter("status", status)
                .setParameter("updatedAt", updatedAt)
                .setParameter("actor", actor)
                .setParameter("roleCode", roleCode)
                .executeUpdate();
    }
}
