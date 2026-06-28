package phucuong.ht.modules.deanery.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.util.List;
import java.util.Optional;

@Repository
public class DeaneryRepository extends BaseRepo<Deanery, String> {

    public DeaneryRepository(EntityManager entityManager) {
        super(entityManager, Deanery.class);
    }

    @Transactional
    public Deanery save(Deanery deanery) {
        entityManager.persist(deanery);
        return deanery;
    }

    public boolean existsByDeaneryCode(String deaneryCode) {
        return findById(deaneryCode).isPresent();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Deanery> findByIdWithDiocese(String deaneryCode) {
        return entityManager.createQuery(
                        """
                        select de
                        from Deanery de
                        join fetch de.diocese d
                        where de.deaneryCode = :deaneryCode
                        """,
                        Deanery.class
                )
                .setParameter("deaneryCode", deaneryCode)
                .getResultStream()
                .findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Deanery> findAllByDeaneryCodesWithDiocese(List<String> deaneryCodes) {
        if (deaneryCodes == null || deaneryCodes.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                        select de
                        from Deanery de
                        join fetch de.diocese d
                        where de.deaneryCode in :deaneryCodes
                        """,
                        Deanery.class
                )
                .setParameter("deaneryCodes", deaneryCodes)
                .getResultList();
    }

    public Optional<String> findDioceseCodeByDeaneryCode(String deaneryCode) {
        return entityManager.createQuery(
                        """
                        select de.diocese.dioceseCode
                        from Deanery de
                        where de.deaneryCode = :deaneryCode
                        """,
                        String.class
                )
                .setParameter("deaneryCode", deaneryCode)
                .getResultStream()
                .findFirst();
    }

    public Diocese getDioceseReference(String dioceseCode) {
        return entityManager.getReference(Diocese.class, dioceseCode);
    }
}
