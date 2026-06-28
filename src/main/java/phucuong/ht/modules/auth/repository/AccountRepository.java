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

    @Query("""
            select d.dioceseCode
            from Account a
            join a.leader l
            join l.parish p
            join p.deanery de
            join de.diocese d
            where a.username = :username
              and a.status = true
              and l.status = true
              and p.status = true
              and de.status = true
              and d.status = true
            """)
    Optional<String> findActiveDioceseCodeByUsername(@Param("username") String username);

    @Query("""
            select de.deaneryCode
            from Account a
            join a.leader l
            join l.parish p
            join p.deanery de
            where a.username = :username
              and a.status = true
              and l.status = true
              and p.status = true
              and de.status = true
            """)
    Optional<String> findActiveDeaneryCodeByUsername(@Param("username") String username);

    boolean existsByUsername(String username);
}
