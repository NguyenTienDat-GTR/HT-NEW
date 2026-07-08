package phucuong.ht.modules.training.scoreFormulaTemplate.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.entity.ScoreFormulaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ScoreFormulaTemplateRepository extends BaseRepo<ScoreFormulaTemplate, String> {

    public ScoreFormulaTemplateRepository(EntityManager entityManager) {
        super(entityManager, ScoreFormulaTemplate.class);
    }

    @jakarta.transaction.Transactional
    public ScoreFormulaTemplate save(ScoreFormulaTemplate formula) {
        entityManager.persist(formula);
        return formula;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<ScoreFormulaTemplate> findByIdWithComponents(String id) {
        List<ScoreFormulaTemplate> results = entityManager.createQuery(
                        """
                                select distinct formula
                                from ScoreFormulaTemplate formula
                                left join fetch formula.diocese
                                left join fetch formula.deanery
                                left join fetch formula.components componentLink
                                left join fetch componentLink.component component
                                left join fetch component.diocese
                                left join fetch component.deanery
                                where formula.id = :id
                                """,
                        ScoreFormulaTemplate.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDioceseIdByFormulaId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select formula.diocese.id from ScoreFormulaTemplate formula where formula.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDeaneryIdByFormulaId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select formula.deanery.id from ScoreFormulaTemplate formula where formula.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
