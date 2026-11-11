package phucuong.ht.modules.training;

import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.Optional;

@Component
public class TrainingWorkflowSupport {

    /**
     * Điều kiện JPQL "hồ sơ cp đã đạt khóa" khi khóa đó được dùng làm khóa tiên quyết (bí danh hồ sơ phải là cp):
     * loại khóa có cấp chứng nhận thì tính theo chứng nhận được duyệt (đang chờ duyệt, bị từ chối hoặc đã thu hồi đều không tính,
     * kể cả khi kết quả khóa đạt); loại khóa không cấp chứng nhận thì tính theo kết quả khóa.
     * Dùng chung cho kiểm tra điều kiện tham dự và số huynh trưởng đã đạt ở ô chọn khóa tiên quyết.
     */
    public static final String PASSED_AS_PREREQUISITE_JPQL = """
            ((cp.course.courseType.hasCertificate = true
                  and cp.certificateApprovalStatus = phucuong.ht.modules.training.enums.WorkflowApprovalStatus.APPROVED)
              or (cp.course.courseType.hasCertificate = false
                  and cp.result = phucuong.ht.modules.training.enums.CourseResult.COMPLETED))""";

    // Khóa do giáo hạt quản lý (trước đây là khóa DT: SMTL_DT, SMKN_DT).
    public boolean isDeaneryManaged(TrainingCourseType courseType) {
        return courseType != null && courseType.getManagementUnitType() == UnitType.DEANERY;
    }

    // Khóa do giáo hạt quản lý: khóa thường theo loại khóa (cấp giáo hạt); khóa quá khứ không có loại khóa thì theo đơn vị sở hữu
    // (gắn giáo hạt = khóa của admin giáo hạt).
    public boolean isDeaneryManaged(phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse course) {
        if (course == null) {
            return false;
        }
        return course.getCourseType() != null ? isDeaneryManaged(course.getCourseType()) : course.getDeanery() != null;
    }

    // Giáo phận sở hữu khóa: khóa giáo phận lưu trực tiếp, khóa giáo hạt lấy theo giáo hạt. Dùng để tra danh mục cấp độ.
    public java.util.UUID dioceseIdOf(phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse course) {
        if (course == null) {
            return null;
        }
        if (course.getDiocese() != null) {
            return course.getDiocese().getId();
        }
        return course.getDeanery() != null && course.getDeanery().getDiocese() != null ? course.getDeanery().getDiocese().getId() : null;
    }

    // Hồ sơ ngoài đợt: khóa giáo phận có chọn giáo hạt mà khóa dành cho nhưng giáo hạt của huynh trưởng không nằm trong danh sách đó.
    public boolean isOffTarget(phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse course,
                               phucuong.ht.modules.leader.model.entity.Leader leader) {
        if (course == null || leader == null || isDeaneryManaged(course) || course.getTargetDeaneryIds().isEmpty()) {
            return false;
        }
        java.util.UUID deaneryId = leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getId();
        return deaneryId == null || !course.getTargetDeaneryIds().contains(deaneryId);
    }

    public Optional<TrainingCourseType> prerequisiteOf(TrainingCourseType courseType) {
        if (courseType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(courseType.getPrerequisiteType());
    }

    public Optional<String> certificateLevelFor(TrainingCourseType courseType) {
        if (courseType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(courseType.getResultingLevel());
    }

    // Chỉ loại khóa bật cờ "có cấp chứng nhận" mới có chứng nhận và duyệt chứng nhận (cờ này không phụ thuộc cấp độ khi hoàn thành);
    // thăng cấp: có chứng nhận thì theo chứng nhận được duyệt, không thì theo kết quả khóa.
    public boolean requiresCertificate(TrainingCourseType courseType) {
        return courseType != null && courseType.isHasCertificate();
    }

    // Có cấp độ khi hoàn thành (khác rỗng/NONE) thì hoàn thành khóa mới thăng cấp.
    public boolean promotesLevel(phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse course) {
        return certificateLevelFor(course).filter(level -> !"NONE".equals(level)).isPresent();
    }

    // Cấp độ đạt được của khóa: khóa quá khứ lưu trực tiếp trên khóa, khóa thường lấy theo loại khóa.
    public Optional<String> certificateLevelFor(phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse course) {
        if (course == null) {
            return Optional.empty();
        }
        if (course.isPast()) {
            return Optional.ofNullable(course.getResultingLevel());
        }
        return certificateLevelFor(course.getCourseType());
    }

    // Khóa quá khứ không có loại khóa: có cấp độ đạt được thì có chứng nhận (giữ hành vi cũ).
    public boolean requiresCertificate(phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse course) {
        if (course == null) {
            return false;
        }
        return course.isPast() ? promotesLevel(course) : requiresCertificate(course.getCourseType());
    }

    public boolean needsAutomaticEligibilityApproval(TrainingCourseType courseType) {
        return courseType != null;
    }

    // Hồ sơ đủ điều kiện được hệ thống tự duyệt ngay khi gửi đăng ký (khóa giáo hạt: giáo hạt duyệt; khóa giáo phận: giáo phận duyệt).
    public boolean shouldAutoApproveAfterSubmission(TrainingCourseType courseType) {
        return courseType != null;
    }
}
