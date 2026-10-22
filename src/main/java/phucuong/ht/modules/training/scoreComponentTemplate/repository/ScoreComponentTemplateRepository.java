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

    /** Id các thành phần điểm đã có ít nhất một điểm được nhập trong bất kỳ khóa huấn luyện nào. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Set<String> findIdsWithEnteredScores(java.util.Collection<String> componentIds) {
        if (componentIds == null || componentIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(entityManager.createQuery(
                        """
                                select distinct score.component.id
                                from CourseParticipationComponentScore score
                                where score.component.id in :ids
                                  and score.score is not null
                                """,
                        String.class
                )
                .setParameter("ids", componentIds)
                .getResultList());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<ScoreComponentTemplate> findByIdWithScope(String id) {
        List<ScoreComponentTemplate> results = entityManager.createQuery(
                        """
                                select component
                                from ScoreComponentTemplate component
                                left join fetch component.diocese
                                left join fetch component.deanery
                                left join fetch component.courseType
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
                                left join fetch component.courseType
                                where component.id in :ids
                                """,
                        ScoreComponentTemplate.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    // Thành phần điểm đang hoạt động áp dụng cho khóa: cùng loại khóa và cùng đơn vị sở hữu (giáo hạt hoặc giáo phận).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ScoreComponentTemplate> findActiveForCourse(String courseTypeCode, UUID dioceseId, UUID deaneryId) {
        String ownerClause = deaneryId != null ? "component.deanery.id = :ownerId" : "component.diocese.id = :ownerId";
        return entityManager.createQuery(
                        "select component from ScoreComponentTemplate component "
                                + "where component.courseType.typeCode = :courseTypeCode and component.status = true and "
                                + ownerClause + " order by component.displayOrder asc, component.name asc",
                        ScoreComponentTemplate.class
                )
                .setParameter("courseTypeCode", courseTypeCode)
                .setParameter("ownerId", deaneryId != null ? deaneryId : dioceseId)
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
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    public Optional<UUID> findDeaneryIdByComponentId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select component.deanery.id from ScoreComponentTemplate component where component.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
