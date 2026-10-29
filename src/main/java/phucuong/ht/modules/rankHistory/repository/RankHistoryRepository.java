package phucuong.ht.modules.rankHistory.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RankHistoryRepository {

    private final EntityManager entityManager;

    public RankHistoryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public RankHistory save(RankHistory rankHistory) {
        entityManager.persist(rankHistory);
        return rankHistory;
    }

    public List<RankHistory> findAllByLeaderId(UUID leaderId) {
        return entityManager.createQuery(
                        """
                                select rh
                                from RankHistory rh
                                where rh.leader.id = :leaderId
                                order by rh.promotionDate desc, rh.id desc
                                """,
                        RankHistory.class
                )
                .setParameter("leaderId", leaderId)
                .getResultList();
    }

    // Tên khóa theo mã khóa (khóa quá khứ có mã là UUID nên không đọc được nếu hiển thị thẳng).
    public Map<String, String> findCourseNamesByCodes(Collection<String> courseCodes) {
        Map<String, String> names = new HashMap<>();
        if (courseCodes == null || courseCodes.isEmpty()) {
            return names;
        }
        List<Object[]> rows = entityManager.createQuery(
                        "select tc.courseCode, tc.courseName from TrainingCourse tc where tc.courseCode in :codes",
                        Object[].class
                )
                .setParameter("codes", courseCodes)
                .getResultList();
        for (Object[] row : rows) {
            names.put((String) row[0], (String) row[1]);
        }
        return names;
    }

    // Năm GẦN NHẤT huynh trưởng đạt được một cấp độ (theo lịch sử cấp độ, bỏ qua lần đã bị hạ cấp).
    public java.util.Optional<Integer> findLatestYearReachedLevel(UUID leaderId, String level) {
        return entityManager.createQuery(
                        """
                                select max(extract(year from rh.promotionDate))
                                from RankHistory rh
                                where rh.leader.id = :leaderId
                                  and rh.newLevel = :level
                                  and rh.reverted = false
                                  and rh.promotionDate is not null
                                """,
                        Integer.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("level", level)
                .getResultList()
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    // Lần thăng cấp gần nhất chưa bị hạ cấp của huynh trưởng do một khóa sinh ra.
    public java.util.Optional<RankHistory> findLatestActivePromotion(UUID leaderId, String courseCode) {
        return entityManager.createQuery(
                        """
                                select rh
                                from RankHistory rh
                                where rh.leader.id = :leaderId
                                  and rh.courseCode = :courseCode
                                  and rh.reverted = false
                                  and rh.newLevel is not null
                                order by rh.id desc
                                """,
                        RankHistory.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList()
                .stream().findFirst();
    }
}
