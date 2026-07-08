package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
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

    public int deactivateExpiredAssignments(OffsetDateTime expiresAtThreshold, OffsetDateTime updatedAt, String actor) {
        return entityManager.createQuery(
                        """
                        update RolePermission rp
                        set rp.status = false,
                            rp.updatedAt = :updatedAt,
                            rp.updatedBy = :actor
                        where rp.status = true
                          and rp.expiresAt is not null
                          and rp.expiresAt <= :expiresAtThreshold
                        """
                )
                .setParameter("updatedAt", updatedAt)
                .setParameter("actor", actor)
                .setParameter("expiresAtThreshold", expiresAtThreshold)
                .executeUpdate();
    }
}
