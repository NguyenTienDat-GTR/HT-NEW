package phucuong.ht.modules.training;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

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
