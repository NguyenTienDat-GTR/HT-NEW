package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.RolePermissionUnitOverride;
import phucuong.ht.modules.system.model.enums.RolePermissionOverrideType;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RolePermissionUnitOverrideRepository extends BaseRepo<RolePermissionUnitOverride, String> {

    /** Điều kiện "đang hiệu lực" của một dòng ngoại lệ (chưa thu hồi, bật, chưa quá hạn). */
    private static final String ACTIVE = """
            o.status = true and o.revokedAt is null
            and (o.expiresAt is null or o.expiresAt > CURRENT_TIMESTAMP)
            """;

    public RolePermissionUnitOverrideRepository(EntityManager entityManager) {
        super(entityManager, RolePermissionUnitOverride.class);
    }

    @Transactional
    public RolePermissionUnitOverride save(RolePermissionUnitOverride entity) {
        entityManager.persist(entity);
        return entity;
    }

    /** Dòng (kể cả đã thu hồi) của đúng một đơn vị, để cấp lại bằng chính dòng đó. */
    public Optional<RolePermissionUnitOverride> find(String roleCode, String permissionCode, UUID unitId, RolePermissionOverrideType type) {
        TypedQuery<RolePermissionUnitOverride> query = entityManager.createQuery(
                """
                select o from RolePermissionUnitOverride o
                join fetch o.role join fetch o.permission
                where o.role.roleCode = :roleCode and o.permission.permissionCode = :permissionCode
                  and o.unitId = :unitId and o.overrideType = :type
                """,
                RolePermissionUnitOverride.class);
        query.setParameter("roleCode", roleCode);
        query.setParameter("permissionCode", permissionCode);
        query.setParameter("unitId", unitId);
        query.setParameter("type", type);
        query.setMaxResults(1);
        return query.getResultList().stream().findFirst();
    }

    /** Các dòng đang hiệu lực của một quyền trong chuỗi đơn vị (giáo phận, giáo hạt, giáo xứ). */
    public List<RolePermissionUnitOverride> findActiveInChain(String roleCode, String permissionCode, Collection<UUID> unitIds, RolePermissionOverrideType type) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select o from RolePermissionUnitOverride o where o.role.roleCode = :roleCode"
                                + " and o.permission.permissionCode = :permissionCode and o.overrideType = :type"
                                + " and o.unitId in :unitIds and " + ACTIVE,
                        RolePermissionUnitOverride.class)
                .setParameter("roleCode", roleCode)
                .setParameter("permissionCode", permissionCode)
                .setParameter("type", type)
                .setParameter("unitIds", unitIds)
                .getResultList();
    }

    /** Mọi dòng ngoại lệ đang hiệu lực (cả cấp riêng và thu hồi riêng, mọi vai trò) tại các đơn vị đã cho. */
    public List<RolePermissionUnitOverride> findActiveInUnits(Collection<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select o from RolePermissionUnitOverride o join fetch o.role join fetch o.permission"
                                + " where o.unitId in :unitIds and " + ACTIVE,
                        RolePermissionUnitOverride.class)
                .setParameter("unitIds", unitIds)
                .getResultList();
    }

    /** Mọi dòng ngoại lệ đang hiệu lực của một vai trò trong chuỗi đơn vị (cả cấp riêng và thu hồi riêng). */
    public List<RolePermissionUnitOverride> findActiveByRoleInChain(String roleCode, Collection<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select o from RolePermissionUnitOverride o join fetch o.permission"
                                + " where o.role.roleCode = :roleCode and o.unitId in :unitIds and " + ACTIVE,
                        RolePermissionUnitOverride.class)
                .setParameter("roleCode", roleCode)
                .setParameter("unitIds", unitIds)
                .getResultList();
    }

    /** Các GRANT sắp bị thu hồi do hết hạn, khóa dòng để nhiều node chỉ xử lý / thông báo một lần. */
    @Transactional
    public List<RolePermissionUnitOverride> findExpiredGrants(OffsetDateTime threshold) {
        return entityManager.createQuery(
                        "select o from RolePermissionUnitOverride o where o.overrideType = :type and o.revokedAt is null"
                                + " and o.expiresAt is not null and o.expiresAt <= :threshold",
                        RolePermissionUnitOverride.class)
                .setParameter("type", RolePermissionOverrideType.GRANT)
                .setParameter("threshold", threshold)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Transactional
    public int deactivateExpiredGrants(OffsetDateTime threshold, OffsetDateTime updatedAt, String actor) {
        return entityManager.createQuery(
                        """
                        update RolePermissionUnitOverride o
                        set o.revokedAt = o.expiresAt,
                            o.revokedBy = :actor,
                            o.revocationReason = phucuong.ht.modules.system.model.enums.PermissionRevocationReason.EXPIRED,
                            o.updatedAt = :updatedAt,
                            o.updatedBy = :actor
                        where o.overrideType = phucuong.ht.modules.system.model.enums.RolePermissionOverrideType.GRANT
                          and o.revokedAt is null and o.expiresAt is not null and o.expiresAt <= :threshold
                        """)
                .setParameter("actor", actor)
                .setParameter("updatedAt", updatedAt)
                .setParameter("threshold", threshold)
                .executeUpdate();
    }
}
