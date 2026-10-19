package phucuong.ht.modules.training.courseParticipation.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CourseParticipationRepository extends BaseRepo<CourseParticipation, String> {

    public CourseParticipationRepository(EntityManager entityManager) {
        super(entityManager, CourseParticipation.class);
    }

    @Transactional
    public CourseParticipation save(CourseParticipation participation) {
        entityManager.persist(participation);
        return participation;
    }

    @Transactional
    public void delete(CourseParticipation participation) {
        entityManager.remove(entityManager.contains(participation) ? participation : entityManager.merge(participation));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<CourseParticipation> findByIdWithLeaderAndCourse(String id) {
        List<CourseParticipation> results = entityManager.createQuery(
                        """
                                select cp
                                from CourseParticipation cp
                                join fetch cp.leader l
                                join fetch l.parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                join fetch cp.course c
                                left join fetch c.courseType courseType
                                left join fetch courseType.prerequisiteType
                                left join fetch c.deanery
                                left join fetch c.diocese
                                left join fetch cp.componentScores participationScores
                                left join fetch participationScores.component
                                where cp.id = :id
                                """,
                        CourseParticipation.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public boolean existsApprovedParticipationByLeaderAndCourseType(UUID leaderId, String prerequisiteTypeCode) {
        Long count = entityManager.createQuery(
                        """
                                select count(cp)
                                from CourseParticipation cp
                                where cp.leader.id = :leaderId
                                  and cp.course.courseType.typeCode = :typeCode
                                  and cp.result = phucuong.ht.modules.training.enums.CourseResult.COMPLETED
                                """,
                        Long.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("typeCode", prerequisiteTypeCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    // Năm sớm nhất huynh trưởng đạt khóa thuộc loại này: ưu tiên năm duyệt chứng chỉ, nếu thiếu thì lấy năm tổ chức/kết thúc khóa.
    public Optional<Integer> findEarliestCompletedYearByLeaderAndCourseType(UUID leaderId, String typeCode) {
        List<Integer> years = entityManager.createQuery(
                        """
                                select coalesce(
                                           extract(year from cp.certificateApprovedAt),
                                           cp.course.hostYear,
                                           extract(year from cp.course.endDate))
                                from CourseParticipation cp
                                where cp.leader.id = :leaderId
                                  and cp.course.courseType.typeCode = :typeCode
                                  and cp.result = phucuong.ht.modules.training.enums.CourseResult.COMPLETED
                                """,
                        Integer.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("typeCode", typeCode)
                .getResultList();
        return years.stream().filter(java.util.Objects::nonNull).min(Integer::compareTo);
    }

    public List<CourseParticipation> findAllByCourseCode(String courseCode) {
        return entityManager.createQuery(
                        """
                                select cp
                                from CourseParticipation cp
                                join fetch cp.course c
                                join fetch cp.leader l
                                join fetch l.parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                where c.courseCode = :courseCode
                                """,
                        CourseParticipation.class
                )
                .setParameter("courseCode", courseCode)
                .getResultList();
    }

    public List<CourseParticipation> findAllByIdsWithLeaderAndCourse(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                                select cp
                                from CourseParticipation cp
                                join fetch cp.course c
                                join fetch cp.leader l
                                join fetch l.parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                left join fetch c.courseType courseType
                                left join fetch courseType.prerequisiteType
                                left join fetch cp.componentScores participationScores
                                left join fetch participationScores.component
                                where cp.id in :ids
                                """,
                        CourseParticipation.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    public List<CourseParticipation> findAllByCourseCodeAndDeaneryId(String courseCode, UUID deaneryId) {
        return entityManager.createQuery(
                        """
                                select cp
                                from CourseParticipation cp
                                join fetch cp.course c
                                join fetch cp.leader l
                                join fetch l.parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                where c.courseCode = :courseCode
                                  and de.id = :deaneryId
                                """,
                        CourseParticipation.class
                )
                .setParameter("courseCode", courseCode)
                .setParameter("deaneryId", deaneryId)
                .getResultList();
    }

    public Optional<CourseParticipation> findByLeaderIdAndCourseCode(UUID leaderId, String courseCode) {
        List<CourseParticipation> results = entityManager.createQuery(
                        """
                                select cp
                                from CourseParticipation cp
                                join fetch cp.course c
                                join fetch cp.leader l
                                where l.id = :leaderId
                                  and c.courseCode = :courseCode
                                """,
                        CourseParticipation.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public boolean existsByLeaderIdAndCourseCode(UUID leaderId, String courseCode) {
        Long count = entityManager.createQuery("select count(cp) from CourseParticipation cp where cp.leader.id = :leaderId and cp.course.courseCode = :courseCode", Long.class)
                .setParameter("leaderId", leaderId).setParameter("courseCode", courseCode).getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByCourseCode(String courseCode) {
        Long count = entityManager.createQuery("select count(cp) from CourseParticipation cp where cp.course.courseCode = :courseCode", Long.class)
                .setParameter("courseCode", courseCode).getSingleResult();
        return count != null && count > 0;
    }
}
