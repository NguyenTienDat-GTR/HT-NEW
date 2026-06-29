package phucuong.ht.modules.parish.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.parish.model.entity.Parish;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ParishRepository extends BaseRepo<Parish, UUID> {

    public ParishRepository(EntityManager entityManager) {
        super(entityManager, Parish.class);
    }

    @Transactional
    public Parish save(Parish parish) {
        entityManager.persist(parish);
        return parish;
    }

    public boolean existsByNameAndDeaneryId(String name, UUID deaneryId) {
        Long count = entityManager.createQuery(
                        """
                                select count(p)
                                from Parish p
                                where p.name = :name and p.deanery.id = :deaneryId
                                """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("deaneryId", deaneryId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByNameAndDeaneryIdAndIdNot(String name, UUID deaneryId, UUID id) {
        Long count = entityManager.createQuery(
                        """
                                select count(p)
                                from Parish p
                                where p.name = :name
                                  and p.deanery.id = :deaneryId
                                  and p.id <> :id
                                """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("deaneryId", deaneryId)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count > 0;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Parish> findByIdWithDeaneryAndDiocese(UUID id) {
        List<Parish> results = entityManager.createQuery(
                        """
                                select p
                                from Parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                where p.id = :id
                                """,
                        Parish.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Parish> findAllByIdsWithDeaneryAndDiocese(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                                select p
                                from Parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                where p.id in :ids
                                """,
                        Parish.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    public Optional<UUID> findDeaneryIdByParishId(UUID parishId) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select p.deanery.id
                                from Parish p
                                where p.id = :parishId
                                """,
                        UUID.class
                )
                .setParameter("parishId", parishId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDioceseIdByParishId(UUID parishId) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select p.deanery.diocese.id
                                from Parish p
                                where p.id = :parishId
                                """,
                        UUID.class
                )
                .setParameter("parishId", parishId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
