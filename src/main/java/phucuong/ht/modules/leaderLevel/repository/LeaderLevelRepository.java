package phucuong.ht.modules.leaderLevel.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.leaderLevel.model.entity.LeaderLevel;
import phucuong.ht.modules.leaderLevel.model.entity.LeaderLevelId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LeaderLevelRepository {

    private final EntityManager entityManager;

    public LeaderLevelRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Số nơi đang dùng một cấp độ, để hiển thị và chặn xóa cấp đang được sử dụng.
    public record Usage(long leaders, long courseTypes, long rankHistories, long participations) {
        public boolean inUse() {
            return leaders + courseTypes + rankHistories + participations > 0;
        }
    }

    @Transactional(readOnly = true)
    public List<LeaderLevel> findAllOrdered(UUID dioceseId) {
        return entityManager.createQuery(
                        "select l from LeaderLevel l where l.dioceseId = :dioceseId order by l.displayOrder", LeaderLevel.class)
                .setParameter("dioceseId", dioceseId)
                .getResultList();
    }

    // Mọi cấp độ của mọi giáo phận (tài khoản không thuộc giáo phận nào, ví dụ super admin, chỉ cần nhãn).
    @Transactional(readOnly = true)
    public List<LeaderLevel> findAllAcrossDioceses() {
        return entityManager.createQuery("select l from LeaderLevel l order by l.displayOrder", LeaderLevel.class).getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<LeaderLevel> findByCode(UUID dioceseId, String code) {
        if (dioceseId == null || code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(LeaderLevel.class, new LeaderLevelId(dioceseId, code)));
    }

    @Transactional(readOnly = true)
    public int maxOrder(UUID dioceseId) {
        Integer max = entityManager.createQuery(
                        "select max(l.displayOrder) from LeaderLevel l where l.dioceseId = :dioceseId", Integer.class)
                .setParameter("dioceseId", dioceseId)
                .getSingleResult();
        return max == null ? 0 : max;
    }

    @Transactional(readOnly = true)
    public boolean hasAny(UUID dioceseId) {
        Long count = entityManager.createQuery(
                        "select count(l) from LeaderLevel l where l.dioceseId = :dioceseId", Long.class)
                .setParameter("dioceseId", dioceseId)
                .getSingleResult();
        return count != null && count > 0;
    }

    // Số nơi dùng từng cấp trong giáo phận: huynh trưởng, lịch sử cấp bậc và hồ sơ tham dự của giáo phận đó;
    // loại khóa là danh mục dùng chung nên tính theo mã trên toàn hệ thống.
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Usage> usageByCode(UUID dioceseId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                select lv.level_code,
                       (select count(*) from leader x
                          join parish p on p.id = x.parish_id join deanery de on de.id = p.deanery_id
                         where de.diocese_id = :dioceseId and x.leader_level = lv.level_code),
                       (select count(*) from training_course_type t
                         where t.required_current_level = lv.level_code or t.resulting_level = lv.level_code),
                       (select count(*) from rank_history r
                          join leader x on x.id = r.leader_id
                          join parish p on p.id = x.parish_id join deanery de on de.id = p.deanery_id
                         where de.diocese_id = :dioceseId and (r.old_level = lv.level_code or r.new_level = lv.level_code)),
                       (select count(*) from course_participation cp
                          join leader x on x.id = cp.leader_id
                          join parish p on p.id = x.parish_id join deanery de on de.id = p.deanery_id
                         where de.diocese_id = :dioceseId and cp.target_level = lv.level_code)
                from leader_level lv
                where lv.diocese_id = :dioceseId
                """)
                .setParameter("dioceseId", dioceseId)
                .getResultList();
        Map<String, Usage> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], new Usage(((Number) row[1]).longValue(), ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(), ((Number) row[4]).longValue()));
        }
        return result;
    }

    @Transactional
    public LeaderLevel persist(LeaderLevel level) {
        entityManager.persist(level);
        return level;
    }

    @Transactional
    public LeaderLevel merge(LeaderLevel level) {
        return entityManager.merge(level);
    }

    @Transactional
    public void remove(UUID dioceseId, String code) {
        LeaderLevel managed = entityManager.find(LeaderLevel.class, new LeaderLevelId(dioceseId, code));
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
