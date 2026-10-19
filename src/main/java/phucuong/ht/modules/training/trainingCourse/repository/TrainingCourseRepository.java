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

    // Khóa đang mở đăng ký thuộc giáo phận (khóa thường) hoặc giáo hạt (khóa Dự trưởng) của tài khoản.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findOpenForRegistration(UUID dioceseId, UUID deaneryId) {
        StringBuilder jpql = new StringBuilder("""
                select tc
                from TrainingCourse tc
                left join fetch tc.courseType
                left join fetch tc.diocese
                left join fetch tc.deanery
                where tc.status = true
                  and tc.registrationStatus = phucuong.ht.modules.training.enums.CourseRegistrationStatus.OPEN
                  and (1 = 0
                """);
        if (dioceseId != null) jpql.append(" or tc.diocese.id = :dioceseId");
        if (deaneryId != null) jpql.append(" or tc.deanery.id = :deaneryId");
        jpql.append(") order by tc.startDate desc, tc.courseName");
        var query = entityManager.createQuery(jpql.toString(), TrainingCourse.class);
        if (dioceseId != null) query.setParameter("dioceseId", dioceseId);
        if (deaneryId != null) query.setParameter("deaneryId", deaneryId);
        return query.getResultList();
    }

    // Các khóa chưa ở trạng thái cuối (đăng ký và tổ chức đều đã ENDED thì không cần quét).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findCoursesWithOpenStatuses() {
        return entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                left join fetch tc.courseType
                                left join fetch tc.diocese
                                left join fetch tc.deanery
                                where tc.registrationStatus <> phucuong.ht.modules.training.enums.CourseRegistrationStatus.ENDED
                                   or tc.organizingStatus <> phucuong.ht.modules.training.enums.CourseOrganizingStatus.ENDED
                                """,
                        TrainingCourse.class
                )
                .getResultList();
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
