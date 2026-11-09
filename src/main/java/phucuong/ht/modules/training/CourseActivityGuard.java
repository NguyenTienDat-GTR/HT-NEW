package phucuong.ht.modules.training;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.service.TrainingCourseStatusSupport;

// Khóa huấn luyện bị vô hiệu hóa (status = false) thì không cho thao tác bất cứ gì trong khóa đó
// (đăng ký, duyệt, chấm điểm, kết thúc, sửa, duyệt / thu hồi / cấp lại chứng nhận...). Chỉ xem được.
// Khóa do đơn vị tổ chức (giáo phận / giáo hạt) đã bị tắt hoặc có đơn vị cha bị tắt cũng bị đóng băng như vậy.
@Component
@RequiredArgsConstructor
public class CourseActivityGuard {

    private final CommonValidationHelper validationHelper;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;

    public void requireActive(TrainingCourse course) {
        requireCourseEnabled(course);
        requireOrganizerOperational(course);
    }

    // Khóa đã kết thúc (thủ công hoặc quá ngày kết thúc) thì chỉ xem, không thao tác hồ sơ nữa.
    public boolean isEnded(TrainingCourse course) {
        return course != null && TrainingCourseStatusSupport.organizingStatus(course, TrainingCourseStatusSupport.today())
                == phucuong.ht.modules.training.enums.CourseOrganizingStatus.ENDED;
    }

    // Khóa đã diễn ra (đã tới ngày bắt đầu, hoặc đã kết thúc): đóng đăng ký, không sao chép / nhập Excel thêm vào danh sách tham dự nữa.
    public boolean isStarted(TrainingCourse course) {
        return TrainingCourseStatusSupport.isCourseStarted(course, java.time.OffsetDateTime.now());
    }

    public void requireNotStarted(TrainingCourse course, String action) {
        if (isStarted(course)) {
            throw validationHelper.conflict("Khóa huấn luyện đã diễn ra nên không thể " + action);
        }
    }

    public void requireNotEnded(TrainingCourse course) {
        if (isEnded(course)) {
            throw validationHelper.conflict("Khóa huấn luyện đã kết thúc nên không thể thao tác");
        }
    }

    // Dùng cho thao tác thu hồi chứng nhận: vẫn cho phép khi đơn vị tổ chức tạm ngưng, chỉ chặn khi khóa bị tắt.
    public void requireCourseEnabled(TrainingCourse course) {
        if (course != null && Boolean.FALSE.equals(course.getStatus())) {
            throw validationHelper.conflict("Khóa huấn luyện đã bị vô hiệu hóa nên không thể thao tác. Hãy bật lại khóa trước.");
        }
    }

    private void requireOrganizerOperational(TrainingCourse course) {
        if (course == null || unitOperationalStatusService == null) {
            return;
        }
        if (course.getDeanery() != null && course.getDeanery().getId() != null) {
            unitOperationalStatusService.requireUnitOperational(UnitType.DEANERY, course.getDeanery().getId());
        } else if (course.getDiocese() != null && course.getDiocese().getId() != null) {
            unitOperationalStatusService.requireUnitOperational(UnitType.DIOCESE, course.getDiocese().getId());
        }
    }
}
