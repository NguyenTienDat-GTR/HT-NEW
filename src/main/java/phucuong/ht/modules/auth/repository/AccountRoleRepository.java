package phucuong.ht.modules.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import phucuong.ht.modules.system.model.entity.AccountRole;

import java.util.List;
import java.util.Optional;

public interface AccountRoleRepository extends JpaRepository<AccountRole, String> {

    @Query("""
            select ar.role.roleCode
            from AccountRole ar
            where ar.account.username = :username
              and ar.status = true
              and ar.role.status = true
              and (ar.expiresAt is null or ar.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<String> findActiveRoleCodesByUsername(@Param("username") String username);

    @Query("""
            select ar
            from AccountRole ar
            join fetch ar.role
            where ar.account.username = :username
              and ar.status = true
              and ar.role.status = true
              and (ar.expiresAt is null or ar.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<AccountRole> findActiveByUsername(@Param("username") String username);

    Optional<AccountRole> findByAccountUsernameAndRoleRoleCode(String username, String roleCode);

    List<AccountRole> findByAccountUsername(String username);
}
