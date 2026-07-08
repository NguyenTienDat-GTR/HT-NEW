package phucuong.ht.modules.training.scoreComponentTemplate.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ScoreComponentTemplateRepository extends BaseRepo<ScoreComponentTemplate, String> {

    public ScoreComponentTemplateRepository(EntityManager entityManager) {
        super(entityManager, ScoreComponentTemplate.class);
    }

    @jakarta.transaction.Transactional
    public ScoreComponentTemplate save(ScoreComponentTemplate component) {
        entityManager.persist(component);
        return component;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<ScoreComponentTemplate> findByIdWithScope(String id) {
        List<ScoreComponentTemplate> results = entityManager.createQuery(
                        """
                                select component
                                from ScoreComponentTemplate component
                                left join fetch component.diocese
                                left join fetch component.deanery
                                where component.id = :id
                                """,
                        ScoreComponentTemplate.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ScoreComponentTemplate> findAllByIdsWithScope(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        """
                                select component
                                from ScoreComponentTemplate component
                                left join fetch component.diocese
                                left join fetch component.deanery
                                where component.id in :ids
                                """,
                        ScoreComponentTemplate.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    public Optional<UUID> findDioceseIdByComponentId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select component.diocese.id from ScoreComponentTemplate component where component.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDeaneryIdByComponentId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select component.deanery.id from ScoreComponentTemplate component where component.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
