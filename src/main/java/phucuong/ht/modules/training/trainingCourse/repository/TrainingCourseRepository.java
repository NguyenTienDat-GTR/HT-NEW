package phucuong.ht.modules.training.trainingCourse.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TrainingCourseRepository extends BaseRepo<TrainingCourse, String> {

    public TrainingCourseRepository(EntityManager entityManager) {
        super(entityManager, TrainingCourse.class);
    }

    @Transactional
    public TrainingCourse save(TrainingCourse course) {
        entityManager.persist(course);
        return course;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<TrainingCourse> findByCodeWithType(String courseCode) {
        List<TrainingCourse> results = entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                left join fetch tc.courseType courseType
                                left join fetch courseType.prerequisiteType
                                left join fetch tc.diocese
                                left join fetch tc.deanery
                                where tc.courseCode = :courseCode
                                """,
                        TrainingCourse.class
                )
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean existsByCourseTypeCode(String courseTypeCode) {
        Long count = entityManager.createQuery(
                        "select count(tc) from TrainingCourse tc where tc.courseType.typeCode = :typeCode",
                        Long.class
                )
                .setParameter("typeCode", courseTypeCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    public Optional<UUID> findDioceseIdByCourseCode(String courseCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select tc.diocese.id
                                from TrainingCourse tc
                                where tc.courseCode = :courseCode
                                """,
                        UUID.class
                )
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDeaneryIdByCourseCode(String courseCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select tc.deanery.id
                                from TrainingCourse tc
                                where tc.courseCode = :courseCode
                                """,
                        UUID.class
                )
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
