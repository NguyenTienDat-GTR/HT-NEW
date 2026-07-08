package phucuong.ht.modules.training.courseParticipation.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.enums.CourseType;

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
                                left join fetch c.requirement
                                left join fetch c.deanery
                                left join fetch c.diocese
                                where cp.id = :id
                                """,
                        CourseParticipation.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public boolean existsApprovedParticipationByLeaderAndCourseType(UUID leaderId, CourseType courseType) {
        Long count = entityManager.createQuery(
                        """
                                select count(cp)
                                from CourseParticipation cp
                                where cp.leader.id = :leaderId
                                  and cp.course.courseType = :courseType
                                  and cp.result = phucuong.ht.modules.training.enums.CourseResult.COMPLETED
                                """,
                        Long.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("courseType", courseType)
                .getSingleResult();
        return count != null && count > 0;
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
}
