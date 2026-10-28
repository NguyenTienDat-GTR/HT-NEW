package phucuong.ht.modules.training.crossDeanery.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryAccessDecision;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryCourseAccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CrossDeaneryCourseAccessRepository extends BaseRepo<CrossDeaneryCourseAccess, String> {

    public CrossDeaneryCourseAccessRepository(EntityManager entityManager) {
        super(entityManager, CrossDeaneryCourseAccess.class);
    }

    public CrossDeaneryCourseAccess save(CrossDeaneryCourseAccess access) {
        entityManager.persist(access);
        return access;
    }

    public Optional<CrossDeaneryCourseAccess> findByCourseAndDeanery(String courseCode, UUID deaneryId) {
        return entityManager.createQuery("""
                        select a from CrossDeaneryCourseAccess a
                        where a.courseCode = :courseCode and a.deaneryId = :deaneryId
                        """, CrossDeaneryCourseAccess.class)
                .setParameter("courseCode", courseCode)
                .setParameter("deaneryId", deaneryId)
                .getResultList().stream().findFirst();
    }

    public boolean isAllowed(String courseCode, UUID deaneryId) {
        return findByCourseAndDeanery(courseCode, deaneryId)
                .map(access -> access.getDecision() == CrossDeaneryAccessDecision.ALLOWED)
                .orElse(false);
    }

    public List<CrossDeaneryCourseAccess> findByDeanery(UUID deaneryId) {
        return entityManager.createQuery("""
                        select a from CrossDeaneryCourseAccess a where a.deaneryId = :deaneryId
                        """, CrossDeaneryCourseAccess.class)
                .setParameter("deaneryId", deaneryId)
                .getResultList();
    }

    public List<CrossDeaneryCourseAccess> findByCourse(String courseCode) {
        return entityManager.createQuery("""
                        select a from CrossDeaneryCourseAccess a where a.courseCode = :courseCode
                        """, CrossDeaneryCourseAccess.class)
                .setParameter("courseCode", courseCode)
                .getResultList();
    }
}
