package phucuong.ht.modules.auth.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuthPermissionQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<EffectivePermissionRow> findEffectivePermissionsByUsername(String username) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select permission_code, effect
                from v_account_effective_permission
                where username = :username
                """)
                .setParameter("username", username)
                .getResultList();

        return rows.stream()
                .map(row -> new EffectivePermissionRow(String.valueOf(row[0]), String.valueOf(row[1])))
                .toList();
    }

    public record EffectivePermissionRow(String permissionCode, String effect) {
    }
}
