package phucuong.ht.modules.training.courseType.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.List;
import java.util.Optional;

@Repository
public class TrainingCourseTypeRepository extends BaseRepo<TrainingCourseType, String> {

    public TrainingCourseTypeRepository(EntityManager entityManager) {
        super(entityManager, TrainingCourseType.class);
    }

    @Transactional
    public TrainingCourseType save(TrainingCourseType courseType) {
        entityManager.persist(courseType);
        return courseType;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<TrainingCourseType> findByCodeWithPrerequisite(String typeCode) {
        List<TrainingCourseType> results = entityManager.createQuery(
                        """
                                select ct
                                from TrainingCourseType ct
                                left join fetch ct.prerequisiteType
                                where ct.typeCode = :typeCode
                                """,
                        TrainingCourseType.class
                )
                .setParameter("typeCode", typeCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean existsByTypeCode(String typeCode) {
        Long count = entityManager.createQuery(
                        "select count(ct) from TrainingCourseType ct where ct.typeCode = :typeCode",
                        Long.class
                )
                .setParameter("typeCode", typeCode)
                .getSingleResult();
        return count != null && count > 0;
    }
}
