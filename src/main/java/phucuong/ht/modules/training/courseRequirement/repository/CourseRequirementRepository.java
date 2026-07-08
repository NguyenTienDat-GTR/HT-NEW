package phucuong.ht.modules.training.courseRequirement.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseRequirementRepository extends BaseRepo<CourseRequirement, String> {

    public CourseRequirementRepository(EntityManager entityManager) {
        super(entityManager, CourseRequirement.class);
    }

    @Transactional
    public CourseRequirement save(CourseRequirement requirement) {
        entityManager.persist(requirement);
        return requirement;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<CourseRequirement> findByCodeWithScope(String requirementCode) {
        List<CourseRequirement> results = entityManager.createQuery(
                        """
                                select cr
                                from CourseRequirement cr
                                left join fetch cr.diocese
                                left join fetch cr.deanery
                                where cr.requirementCode = :requirementCode
                                """,
                        CourseRequirement.class
                )
                .setParameter("requirementCode", requirementCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDioceseIdByRequirementCode(String requirementCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select cr.diocese.id
                                from CourseRequirement cr
                                where cr.requirementCode = :requirementCode
                                """,
                        UUID.class
                )
                .setParameter("requirementCode", requirementCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDeaneryIdByRequirementCode(String requirementCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select cr.deanery.id
                                from CourseRequirement cr
                                where cr.requirementCode = :requirementCode
                                """,
                        UUID.class
                )
                .setParameter("requirementCode", requirementCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
