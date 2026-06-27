package phucuong.ht.modules.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    @Query("""
            select a
            from Account a
            left join fetch a.leader
            where a.username = :username
              and a.status = true
            """)
    Optional<Account> findActiveByUsername(@Param("username") String username);

    boolean existsByUsername(String username);
}
