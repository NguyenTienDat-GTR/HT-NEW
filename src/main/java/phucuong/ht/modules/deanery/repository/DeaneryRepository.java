package phucuong.ht.modules.deanery.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeaneryRepository extends BaseRepo<Deanery, UUID> {

    public DeaneryRepository(EntityManager entityManager) {
        super(entityManager, Deanery.class);
    }

    @Transactional
    public Deanery save(Deanery deanery) {
        entityManager.persist(deanery);
        return deanery;
    }

    public boolean existsByNameAndDioceseId(String name, UUID dioceseId) {
        Long count = entityManager.createQuery(
                        """
                        select count(de)
                        from Deanery de
                        where de.name = :name and de.diocese.id = :dioceseId
                        """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("dioceseId", dioceseId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByNameAndDioceseIdAndIdNot(String name, UUID dioceseId, UUID id) {
        Long count = entityManager.createQuery(
                        """
                        select count(de)
                        from Deanery de
                        where de.name = :name
                          and de.diocese.id = :dioceseId
                          and de.id <> :id
                        """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("dioceseId", dioceseId)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count > 0;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Deanery> findByIdWithDiocese(UUID id) {
        List<Deanery> results = entityManager.createQuery(
                        """
                        select de
                        from Deanery de
                        join fetch de.diocese d
                        where de.id = :id
                        """,
                        Deanery.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Deanery> findAllByIdsWithDiocese(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                        select de
                        from Deanery de
                        join fetch de.diocese d
                        where de.id in :ids
                        """,
                        Deanery.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    public Optional<UUID> findDioceseIdByDeaneryId(UUID deaneryId) {
        List<UUID> results = entityManager.createQuery(
                        """
                        select de.diocese.id
                        from Deanery de
                        where de.id = :deaneryId
                        """,
                        UUID.class
                )
                .setParameter("deaneryId", deaneryId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Diocese getDioceseReference(UUID dioceseId) {
        return entityManager.getReference(Diocese.class, dioceseId);
    }
}
