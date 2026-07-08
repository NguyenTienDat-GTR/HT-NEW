package phucuong.ht.modules.rankHistory.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;

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
}
