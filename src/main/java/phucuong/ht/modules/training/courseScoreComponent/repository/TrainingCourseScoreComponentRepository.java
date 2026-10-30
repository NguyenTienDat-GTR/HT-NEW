package phucuong.ht.modules.training.courseScoreComponent.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class TrainingCourseScoreComponentRepository extends BaseRepo<TrainingCourseScoreComponent, String> {

    public TrainingCourseScoreComponentRepository(EntityManager entityManager) {
        super(entityManager, TrainingCourseScoreComponent.class);
    }

    @Transactional
    public TrainingCourseScoreComponent save(TrainingCourseScoreComponent item) {
        entityManager.persist(item);
        return item;
    }

    @Transactional
    public void remove(TrainingCourseScoreComponent item) {
        entityManager.remove(entityManager.contains(item) ? item : entityManager.merge(item));
    }

    /** Thành phần điểm của khóa, theo thứ tự hiển thị. */
    @Transactional(readOnly = true)
    public List<TrainingCourseScoreComponent> findByCourseCode(String courseCode) {
        return entityManager.createQuery(
                        """
                                select cc from TrainingCourseScoreComponent cc
                                where cc.course.courseCode = :courseCode and cc.status = true
                                order by cc.displayOrder asc, cc.name asc
                                """,
                        TrainingCourseScoreComponent.class)
                .setParameter("courseCode", courseCode)
                .getResultList();
    }

    /** Id các thành phần của khóa đã có ít nhất một điểm được nhập. */
    @Transactional(readOnly = true)
    public Set<String> findComponentIdsWithScoresInCourse(String courseCode) {
        return new HashSet<>(entityManager.createQuery(
                        """
                                select distinct s.component.id from CourseParticipationComponentScore s
                                where s.component.course.courseCode = :courseCode and s.score is not null
                                """,
                        String.class)
                .setParameter("courseCode", courseCode)
                .getResultList());
    }
}
