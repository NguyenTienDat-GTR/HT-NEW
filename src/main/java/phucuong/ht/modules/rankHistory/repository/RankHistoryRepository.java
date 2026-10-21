package phucuong.ht.modules.rankHistory.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;

import java.util.List;
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
