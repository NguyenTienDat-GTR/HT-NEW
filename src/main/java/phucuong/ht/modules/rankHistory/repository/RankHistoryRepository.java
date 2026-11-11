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

    // Năm đạt từng cấp độ của nhiều huynh trưởng (cấp độ -> năm GẦN NHẤT), bỏ qua lần đã bị hạ cấp.
    // Năm đạt: năm duyệt chứng nhận của khóa sinh ra lần thăng cấp; khóa không cấp chứng nhận thì năm tổ chức (hostYear, thiếu thì năm kết thúc) của khóa;
    // dòng lịch sử không gắn khóa (nhập tay, dữ liệu cũ) thì năm ngày thăng cấp.
    public Map<UUID, Map<String, Integer>> findAchievedYearsByLeaders(Collection<UUID> leaderIds) {
        Map<UUID, Map<String, Integer>> result = new HashMap<>();
        if (leaderIds == null || leaderIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager.createQuery(
                        """
                                select rh.leader.id, rh.newLevel, rh.promotionDate, cp.certificateApprovedAt, c.hostYear, c.endDate
                                from RankHistory rh
                                left join TrainingCourse c on c.courseCode = rh.courseCode
                                left join CourseParticipation cp on cp.course = c and cp.leader = rh.leader
                                where rh.leader.id in :leaderIds
                                  and rh.reverted = false
                                  and rh.newLevel is not null
                                """,
                        Object[].class
                )
                .setParameter("leaderIds", leaderIds)
                .getResultList();
        for (Object[] row : rows) {
            Integer year = achievedYear((java.time.LocalDate) row[2], (java.time.OffsetDateTime) row[3], (Integer) row[4], (java.time.LocalDate) row[5]);
            if (year != null) {
                result.computeIfAbsent((UUID) row[0], key -> new HashMap<>()).merge((String) row[1], year, Math::max);
            }
        }
        return result;
    }

    static Integer achievedYear(java.time.LocalDate promotionDate, java.time.OffsetDateTime certificateApprovedAt, Integer hostYear, java.time.LocalDate courseEndDate) {
        if (certificateApprovedAt != null) {
            return certificateApprovedAt.getYear();
        }
        if (hostYear != null) {
            return hostYear;
        }
        if (courseEndDate != null) {
            return courseEndDate.getYear();
        }
        return promotionDate == null ? null : promotionDate.getYear();
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
