package phucuong.ht.common.concurrency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

/**
 * Khóa hàng bằng {@code SELECT ... FOR UPDATE} rồi nạp lại entity: người đến sau chờ người trước commit, rồi đọc đúng trạng thái đã commit
 * (không dùng bản đã nạp trước khi chờ). Chống nhiều người cùng duyệt / thu hồi một chứng nhận, cùng đổi cấp một huynh trưởng.
 *
 * <p>Thứ tự khóa thống nhất để không deadlock: khóa → chứng nhận → huynh trưởng.
 */
@Component
public class EntityLockService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public Certificate lock(Certificate certificate) {
        lockRow("certificate", "certificate_code", certificate.getCertificateCode());
        entityManager.refresh(certificate);
        return certificate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Leader lock(Leader leader) {
        lockRow("leader", "id", leader.getId());
        entityManager.refresh(leader);
        return leader;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TrainingCourse lock(TrainingCourse course) {
        lockRow("training_course", "course_code", course.getCourseCode());
        entityManager.refresh(course);
        return course;
    }

    private void lockRow(String table, String idColumn, Object id) {
        // Tên bảng / cột là hằng cố định ở trên, chỉ giá trị id được truyền bằng tham số.
        entityManager.createNativeQuery("select 1 from " + table + " where " + idColumn + " = :id for update")
                .setParameter("id", id)
                .getResultList();
    }
}
