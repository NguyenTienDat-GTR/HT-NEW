package phucuong.ht.modules.training.courseType.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.List;
import java.util.Optional;

@Repository
public class TrainingCourseTypeRepository extends BaseRepo<TrainingCourseType, String> {

    public TrainingCourseTypeRepository(EntityManager entityManager) {
        super(entityManager, TrainingCourseType.class);
    }

    @Transactional
    public TrainingCourseType save(TrainingCourseType courseType) {
        entityManager.persist(courseType);
        return courseType;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<TrainingCourseType> findByCodeWithPrerequisite(String typeCode) {
        List<TrainingCourseType> results = entityManager.createQuery(
                        """
                                select ct
                                from TrainingCourseType ct
                                left join fetch ct.prerequisiteType
                                where ct.typeCode = :typeCode
                                """,
                        TrainingCourseType.class
                )
                .setParameter("typeCode", typeCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    // Loại khóa có yêu cầu chứng nhận: có cấp độ đạt được (resultingLevel khác null/NONE), đang hoạt động.
    // dioceseId != null chỉ lấy danh mục của giáo phận đó; null (super admin) lấy của mọi giáo phận.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourseType> findCertifiedTypes(java.util.UUID dioceseId) {
        return entityManager.createQuery(
                        """
                                select ct
                                from TrainingCourseType ct
                                where ct.resultingLevel is not null
                                  and ct.resultingLevel <> :none
                                  and ct.status = true
                                  and (:dioceseId is null or ct.dioceseId = :dioceseId)
                                order by ct.typeName
                                """,
                        TrainingCourseType.class
                )
                .setParameter("none", "NONE")
                .setParameter("dioceseId", dioceseId)
                .getResultList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean existsByTypeCode(String typeCode) {
        Long count = entityManager.createQuery(
                        "select count(ct) from TrainingCourseType ct where ct.typeCode = :typeCode",
                        Long.class
                )
                .setParameter("typeCode", typeCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    // Mọi loại khóa của một giáo phận (cả hai cấp), kèm khóa tiên quyết của từng loại: nguồn chọn khóa tiên quyết.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourseType> findAllInDioceseWithPrerequisite(java.util.UUID dioceseId) {
        return entityManager.createQuery(
                        """
                                select ct
                                from TrainingCourseType ct
                                left join fetch ct.prerequisiteType
                                where ct.dioceseId = :dioceseId
                                order by ct.typeName
                                """,
                        TrainingCourseType.class
                )
                .setParameter("dioceseId", dioceseId)
                .getResultList();
    }

    // Số khóa đã tạo theo từng loại khóa của giáo phận.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Long> countCoursesByType(java.util.UUID dioceseId) {
        return toCountMap(entityManager.createQuery(
                        """
                                select tc.courseType.typeCode, count(tc)
                                from TrainingCourse tc
                                where tc.courseType.dioceseId = :dioceseId
                                group by tc.courseType.typeCode
                                """,
                        Object[].class
                )
                .setParameter("dioceseId", dioceseId)
                .getResultList());
    }

    // Số huynh trưởng (không trùng) đã hoàn thành khóa theo từng loại khóa của giáo phận.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Long> countCompletedLeadersByType(java.util.UUID dioceseId) {
        return toCountMap(entityManager.createQuery(
                        """
                                select cp.course.courseType.typeCode, count(distinct cp.leader.id)
                                from CourseParticipation cp
                                where cp.course.courseType.dioceseId = :dioceseId
                                  and cp.result = phucuong.ht.modules.training.enums.CourseResult.COMPLETED
                                group by cp.course.courseType.typeCode
                                """,
                        Object[].class
                )
                .setParameter("dioceseId", dioceseId)
                .getResultList());
    }

    // Có loại khóa cấp giáo hạt nào đang lấy loại khóa này làm tiên quyết không.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean existsDeaneryTypeWithPrerequisite(String typeCode) {
        Long count = entityManager.createQuery(
                        """
                                select count(ct)
                                from TrainingCourseType ct
                                where ct.prerequisiteType.typeCode = :typeCode
                                  and ct.managementUnitType = phucuong.ht.common.enums.UnitType.DEANERY
                                """,
                        Long.class
                )
                .setParameter("typeCode", typeCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    private static java.util.Map<String, Long> toCountMap(List<Object[]> rows) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
