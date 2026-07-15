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

    public int deactivateExpiredOverrides(OffsetDateTime expiresAtThreshold, OffsetDateTime updatedAt, String actor) {
        return 0;
    }
}
