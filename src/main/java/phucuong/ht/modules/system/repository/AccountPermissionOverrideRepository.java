package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.AccountPermissionOverride;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AccountPermissionOverrideRepository extends BaseRepo<AccountPermissionOverride, String> {

    public AccountPermissionOverrideRepository(EntityManager entityManager) {
        super(entityManager, AccountPermissionOverride.class);
    }

    public Optional<AccountPermissionOverride> findByUsernameAndPermissionCode(String username, String permissionCode) {
        TypedQuery<AccountPermissionOverride> query = entityManager.createQuery(
                """
                select apo
                from AccountPermissionOverride apo
                join fetch apo.account
                join fetch apo.permission
                where apo.account.username = :username
                  and apo.permission.permissionCode = :permissionCode
                """,
                AccountPermissionOverride.class
        );
        query.setParameter("username", username);
        query.setParameter("permissionCode", permissionCode);
        query.setMaxResults(1);
        List<AccountPermissionOverride> results = query.getResultList();
        return results.stream().findFirst();
    }

    public List<String> findPermissionCodesByUsername(String username) {
        TypedQuery<String> query = entityManager.createQuery(
                """
                select apo.permission.permissionCode
                from AccountPermissionOverride apo
                where apo.account.username = :username
                  and apo.revokedAt is null
                  and (apo.expiresAt is null or apo.expiresAt > CURRENT_TIMESTAMP)
                """,
                String.class
        );
        query.setParameter("username", username);
        return query.getResultList();
    }

    /** Các quyền riêng sắp bị thu hồi do hết hạn (chưa thu hồi, đã quá hạn), khóa dòng để nhiều node chỉ xử lý / thông báo một lần. */
    @org.springframework.transaction.annotation.Transactional
    public java.util.List<AccountPermissionOverride> findExpiredOverrides(OffsetDateTime threshold) {
        return entityManager.createQuery(
                        "select apo from AccountPermissionOverride apo where apo.revokedAt is null and apo.expiresAt is not null and apo.expiresAt <= :threshold",
                        AccountPermissionOverride.class)
                .setParameter("threshold", threshold)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    /** Thu hồi vĩnh viễn (lý do EXPIRED) các quyền riêng đã quá hạn, giữ lại bản ghi để hiển thị. */
    @org.springframework.transaction.annotation.Transactional
    public int deactivateExpiredOverrides(OffsetDateTime expiresAtThreshold, OffsetDateTime updatedAt, String actor) {
        return entityManager.createQuery(
                        """
                        update AccountPermissionOverride apo
                        set apo.revokedAt = apo.expiresAt,
                            apo.revokedBy = :actor,
                            apo.revocationReason = phucuong.ht.modules.system.model.enums.PermissionRevocationReason.EXPIRED,
                            apo.updatedAt = :updatedAt,
                            apo.updatedBy = :actor
                        where apo.revokedAt is null
                          and apo.expiresAt is not null
                          and apo.expiresAt <= :threshold
                        """
                )
                .setParameter("actor", actor)
                .setParameter("updatedAt", updatedAt)
                .setParameter("threshold", expiresAtThreshold)
                .executeUpdate();
    }
}
