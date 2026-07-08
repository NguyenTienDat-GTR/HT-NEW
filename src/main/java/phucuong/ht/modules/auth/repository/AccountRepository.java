package phucuong.ht.modules.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.Optional;
import java.util.UUID;

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
            select coalesce(ad.id, addi.id, apdi.id, ld.id)
            from Account a
            left join a.diocese ad
            left join a.deanery ade
            left join ade.diocese addi
            left join a.parish ap
            left join ap.deanery apde
            left join apde.diocese apdi
            left join a.leader l
            left join l.parish p
            left join p.deanery de
            left join de.diocese ld
            where a.username = :username
              and a.status = true
              and (
                    (ad is not null and ad.status = true)
                    or (ad is null and ade is not null and ade.status = true and addi.status = true)
                    or (ad is null and ade is null and ap is not null and ap.status = true and apde.status = true and apdi.status = true)
                    or (ad is null and ade is null and ap is null and l.status = true and p.status = true and de.status = true and ld.status = true)
              )
            """)
    Optional<UUID> findActiveDioceseIdByUsername(@Param("username") String username);

    @Query("""
            select coalesce(ade.id, apde.id, de.id)
            from Account a
            left join a.deanery ade
            left join a.parish ap
            left join ap.deanery apde
            left join a.leader l
            left join l.parish p
            left join p.deanery de
            where a.username = :username
              and a.status = true
              and (
                    (ade is not null and ade.status = true)
                    or (ade is null and ap is not null and ap.status = true and apde.status = true)
                    or (ade is null and ap is null and l.status = true and p.status = true and de.status = true)
              )
            """)
    Optional<UUID> findActiveDeaneryIdByUsername(@Param("username") String username);

    @Query("""
            select coalesce(ap.id, p.id)
            from Account a
            left join a.parish ap
            left join a.leader l
            left join l.parish p
            where a.username = :username
              and a.status = true
              and (
                    (ap is not null and ap.status = true)
                    or (ap is null and l.status = true and p.status = true)
              )
            """)
    Optional<UUID> findActiveParishIdByUsername(@Param("username") String username);

    boolean existsByUsername(String username);
}
