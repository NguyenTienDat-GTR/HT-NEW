package phucuong.ht.modules.training.trainingCourse.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TrainingCourseRepository extends BaseRepo<TrainingCourse, String> {

    public TrainingCourseRepository(EntityManager entityManager) {
        super(entityManager, TrainingCourse.class);
    }

    /**
     * Khóa đang hoạt động (đang diễn ra hoặc đang mở đăng ký, chưa bị vô hiệu hóa) luôn đứng đầu danh sách,
     * bất kể cách sắp xếp người dùng chọn.
     */
    @Override
    protected List<Order> priorityOrders(CriteriaBuilder cb, Root<TrainingCourse> root) {
        Expression<Integer> rank = cb.<Integer>selectCase()
                .when(cb.and(
                        cb.isTrue(root.get("status")),
                        cb.or(
                                cb.equal(root.get("organizingStatus"), CourseOrganizingStatus.ONGOING),
                                cb.equal(root.get("registrationStatus"), CourseRegistrationStatus.OPEN))), 0)
                .otherwise(1);
        return List.of(cb.asc(rank));
    }

    /** Không chọn cột sắp xếp: khóa bắt đầu gần đây xếp trước. */
    @Override
    protected List<Order> defaultOrders(CriteriaBuilder cb, Root<TrainingCourse> root) {
        return List.of(cb.desc(root.get("startDate")), cb.asc(root.get("courseCode")));
    }

    @Transactional
    public TrainingCourse save(TrainingCourse course) {
        entityManager.persist(course);
        return course;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<TrainingCourse> findByCodeWithType(String courseCode) {
        List<TrainingCourse> results = entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                left join fetch tc.courseType courseType
                                left join fetch courseType.prerequisiteType
                                left join fetch tc.diocese
                                left join fetch tc.deanery
                                where tc.courseCode = :courseCode
                                """,
                        TrainingCourse.class
                )
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    // Khóa đang mở đăng ký thuộc giáo phận (khóa thường) hoặc giáo hạt (khóa Dự trưởng) của tài khoản.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findOpenForRegistration(UUID dioceseId, UUID deaneryId) {
        StringBuilder jpql = new StringBuilder("""
                select tc
                from TrainingCourse tc
                left join fetch tc.courseType
                left join fetch tc.diocese
                left join fetch tc.deanery
                where tc.status = true
                  and tc.registrationStatus = phucuong.ht.modules.training.enums.CourseRegistrationStatus.OPEN
                  and (1 = 0
                """);
        if (dioceseId != null) jpql.append(" or tc.diocese.id = :dioceseId");
        if (deaneryId != null) jpql.append(" or tc.deanery.id = :deaneryId");
        jpql.append(") order by tc.startDate desc, tc.courseName");
        var query = entityManager.createQuery(jpql.toString(), TrainingCourse.class);
        if (dioceseId != null) query.setParameter("dioceseId", dioceseId);
        if (deaneryId != null) query.setParameter("deaneryId", deaneryId);
        return query.getResultList();
    }

    // Khóa giáo hạt đang mở đăng ký của các giáo hạt KHÁC trong cùng giáo phận, đã bật mở liên giáo hạt và được giáo hạt của người gửi
    // cho phép (đích của hồ sơ liên giáo hạt).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findOpenDeaneryCoursesOfOtherDeaneries(UUID dioceseId, UUID excludeDeaneryId) {
        return entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                join fetch tc.courseType ct
                                join fetch tc.deanery d
                                where tc.status = true
                                  and tc.pastCourse = false
                                  and tc.openToOtherDeaneries = true
                                  and tc.registrationStatus = phucuong.ht.modules.training.enums.CourseRegistrationStatus.OPEN
                                  and ct.managementUnitType = phucuong.ht.common.enums.UnitType.DEANERY
                                  and d.diocese.id = :dioceseId
                                  and d.id <> :excludeDeaneryId
                                  and exists (
                                      select 1 from CrossDeaneryCourseAccess a
                                      where a.courseCode = tc.courseCode and a.deaneryId = :excludeDeaneryId
                                        and a.decision = phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryAccessDecision.ALLOWED
                                  )
                                order by tc.startDate desc, tc.courseName
                                """,
                        TrainingCourse.class
                )
                .setParameter("dioceseId", dioceseId)
                .setParameter("excludeDeaneryId", excludeDeaneryId)
                .getResultList();
    }

    // Khóa giáo hạt của các giáo hạt KHÁC trong giáo phận đang mở liên giáo hạt và chưa kết thúc đăng ký: giáo hạt nhà xem để cho phép / từ chối.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findCoursesOpenToDeanery(UUID dioceseId, UUID excludeDeaneryId) {
        return entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                join fetch tc.courseType ct
                                join fetch tc.deanery d
                                where tc.status = true
                                  and tc.pastCourse = false
                                  and tc.openToOtherDeaneries = true
                                  and tc.registrationStatus <> phucuong.ht.modules.training.enums.CourseRegistrationStatus.ENDED
                                  and ct.managementUnitType = phucuong.ht.common.enums.UnitType.DEANERY
                                  and d.diocese.id = :dioceseId
                                  and d.id <> :excludeDeaneryId
                                order by tc.startDate desc, tc.courseName
                                """,
                        TrainingCourse.class
                )
                .setParameter("dioceseId", dioceseId)
                .setParameter("excludeDeaneryId", excludeDeaneryId)
                .getResultList();
    }

    // Các khóa chưa ở trạng thái cuối (đăng ký và tổ chức đều đã ENDED thì không cần quét).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findCoursesWithOpenStatuses() {
        return entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                left join fetch tc.courseType
                                left join fetch tc.diocese
                                left join fetch tc.deanery
                                where tc.registrationStatus <> phucuong.ht.modules.training.enums.CourseRegistrationStatus.ENDED
                                   or tc.organizingStatus <> phucuong.ht.modules.training.enums.CourseOrganizingStatus.ENDED
                                """,
                        TrainingCourse.class
                )
                .getResultList();
    }

    // Khóa thường đã kết thúc (đã chốt kết quả chung) và còn bật thuộc một loại khóa: nguồn sao chép danh sách tham dự.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findFinishedByCourseTypeCode(String courseTypeCode) {
        return entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                left join fetch tc.courseType
                                left join fetch tc.diocese
                                left join fetch tc.deanery dn
                                left join fetch dn.diocese
                                where tc.courseType.typeCode = :typeCode
                                  and tc.status = true
                                  and tc.pastCourse = false
                                  and tc.finishedAt is not null
                                order by tc.finishedAt desc, tc.courseName
                                """,
                        TrainingCourse.class
                )
                .setParameter("typeCode", courseTypeCode)
                .getResultList();
    }

    // Khóa có thể làm nguồn sao chép danh sách tham dự trong một giáo phận: khóa thường đã kết thúc hoặc khóa quá khứ
    // (của giáo phận hoặc của giáo hạt thuộc giáo phận), còn bật.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrainingCourse> findCopySources(java.util.UUID dioceseId) {
        return entityManager.createQuery(
                        """
                                select tc
                                from TrainingCourse tc
                                left join fetch tc.courseType
                                left join fetch tc.diocese dc
                                left join fetch tc.deanery dn
                                left join fetch dn.diocese dd
                                where tc.status = true
                                  and (tc.pastCourse = true or tc.finishedAt is not null)
                                  and (dc.id = :dioceseId or dd.id = :dioceseId)
                                order by tc.hostYear desc, tc.courseName
                                """,
                        TrainingCourse.class
                )
                .setParameter("dioceseId", dioceseId)
                .getResultList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean existsByCourseTypeCode(String courseTypeCode) {
        Long count = entityManager.createQuery(
                        "select count(tc) from TrainingCourse tc where tc.courseType.typeCode = :typeCode",
                        Long.class
                )
                .setParameter("typeCode", courseTypeCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    public Optional<UUID> findDioceseIdByCourseCode(String courseCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select tc.diocese.id
                                from TrainingCourse tc
                                where tc.courseCode = :courseCode
                                """,
                        UUID.class
                )
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        // Khóa giáo hạt có thể không gắn giáo phận: bỏ phần tử null để tránh NPE khi bọc Optional.
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    public Optional<UUID> findDeaneryIdByCourseCode(String courseCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select tc.deanery.id
                                from TrainingCourse tc
                                where tc.courseCode = :courseCode
                                """,
                        UUID.class
                )
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        // Khóa giáo phận không gắn giáo hạt: bỏ phần tử null để tránh NPE khi bọc Optional.
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
