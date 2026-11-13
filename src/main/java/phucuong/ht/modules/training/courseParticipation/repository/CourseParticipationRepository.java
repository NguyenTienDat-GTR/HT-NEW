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
                .getResultList();
        // Lọc theo khóa chính nên tối đa một hồ sơ; không dùng setMaxResults vì có join fetch tập hợp (Hibernate sẽ phân trang trong bộ nhớ).
        return results.stream().findFirst();
    }

    // Năm đạt GẦN NHẤT của huynh trưởng ở khóa thuộc loại này (đạt theo TrainingWorkflowSupport.PASSED_AS_PREREQUISITE_JPQL):
    // loại khóa có cấp chứng nhận lấy năm duyệt chứng nhận, loại khóa không cấp chứng nhận lấy năm tổ chức/kết thúc khóa.
    public Optional<Integer> findLatestCompletedYearByLeaderAndCourseType(UUID leaderId, String typeCode) {
        List<Integer> years = entityManager.createQuery(
                        """
                                select coalesce(
                                           extract(year from cp.certificateApprovedAt),
                                           cp.course.hostYear,
                                           extract(year from cp.course.endDate))
                                from CourseParticipation cp
                                where cp.leader.id = :leaderId
                                  and cp.course.courseType.typeCode = :typeCode
                                  and %s
                                """.formatted(phucuong.ht.modules.training.TrainingWorkflowSupport.PASSED_AS_PREREQUISITE_JPQL),
                        Integer.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("typeCode", typeCode)
                .getResultList();
        return years.stream().filter(java.util.Objects::nonNull).max(Integer::compareTo);
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

    // Hồ sơ trong danh sách tham dự (đã duyệt, không hủy) của nhiều khóa, kèm huynh trưởng và đơn vị.
    public List<CourseParticipation> findApprovedByCourseCodes(java.util.Collection<String> courseCodes) {
        if (courseCodes == null || courseCodes.isEmpty()) {
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
                                where c.courseCode in :codes
                                  and cp.approvalStatus = phucuong.ht.modules.training.enums.WorkflowApprovalStatus.APPROVED
                                  and cp.participationStatus <> phucuong.ht.modules.training.enums.ParticipationStatus.CANCELLED
                                """,
                        CourseParticipation.class
                )
                .setParameter("codes", courseCodes)
                .getResultList();
    }

    // Mã huynh trưởng đã có hồ sơ (mọi trạng thái) trong khóa.
    public java.util.Set<UUID> findLeaderIdsByCourseCode(String courseCode) {
        return new java.util.HashSet<>(entityManager.createQuery(
                        "select cp.leader.id from CourseParticipation cp where cp.course.courseCode = :courseCode", UUID.class)
                .setParameter("courseCode", courseCode)
                .getResultList());
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

    /**
     * Như {@link #findByLeaderIdAndCourseCode} cho cả một trang (một câu thay vì mỗi dòng một câu). Có thể trả thêm hồ sơ của
     * cặp huynh trưởng / khóa không có trong trang; nơi gọi tự ghép theo đúng cặp.
     */
    public List<CourseParticipation> findAllByLeaderIdsAndCourseCodes(java.util.Collection<UUID> leaderIds, java.util.Collection<String> courseCodes) {
        if (leaderIds == null || leaderIds.isEmpty() || courseCodes == null || courseCodes.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        """
                                select cp
                                from CourseParticipation cp
                                join fetch cp.course c
                                join fetch cp.leader l
                                left join fetch l.parish
                                where l.id in :leaderIds
                                  and c.courseCode in :courseCodes
                                """,
                        CourseParticipation.class
                )
                .setParameter("leaderIds", leaderIds)
                .setParameter("courseCodes", courseCodes)
                .getResultList();
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

    // Số hồ sơ trong danh sách tham dự (đã duyệt, chưa hủy) và số hồ sơ đạt của từng khóa: [tổng, đạt].
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, long[]> countAttendeesAndPassedByCourseCodes(java.util.Collection<String> courseCodes) {
        java.util.Map<String, long[]> result = new java.util.HashMap<>();
        if (courseCodes == null || courseCodes.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager.createQuery(
                        """
                                select cp.course.courseCode, count(cp),
                                       sum(case when cp.finalResult = phucuong.ht.modules.training.enums.CourseFinalResult.PASSED then 1 else 0 end)
                                from CourseParticipation cp
                                where cp.course.courseCode in :codes
                                  and cp.approvalStatus = phucuong.ht.modules.training.enums.WorkflowApprovalStatus.APPROVED
                                  and cp.participationStatus <> phucuong.ht.modules.training.enums.ParticipationStatus.CANCELLED
                                group by cp.course.courseCode
                                """,
                        Object[].class)
                .setParameter("codes", courseCodes)
                .getResultList();
        for (Object[] row : rows) {
            result.put((String) row[0], new long[]{((Number) row[1]).longValue(), row[2] == null ? 0 : ((Number) row[2]).longValue()});
        }
        return result;
    }

    // Số tham dự viên đã được duyệt của từng khóa (một truy vấn cho cả trang danh sách).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Long> countApprovedByCourseCodes(java.util.Collection<String> courseCodes) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        if (courseCodes == null || courseCodes.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager.createQuery(
                        """
                                select cp.course.courseCode, count(cp)
                                from CourseParticipation cp
                                where cp.course.courseCode in :codes
                                  and cp.approvalStatus = :approved
                                group by cp.course.courseCode
                                """,
                        Object[].class)
                .setParameter("codes", courseCodes)
                .setParameter("approved", phucuong.ht.modules.training.enums.WorkflowApprovalStatus.APPROVED)
                .getResultList();
        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
