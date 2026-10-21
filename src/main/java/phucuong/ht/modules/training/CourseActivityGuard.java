package phucuong.ht.modules.training;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

// Khóa huấn luyện bị vô hiệu hóa (status = false) thì không cho thao tác bất cứ gì trong khóa đó
// (đăng ký, duyệt, chấm điểm, kết thúc, sửa, duyệt / thu hồi / cấp lại chứng nhận...). Chỉ xem được.
@Component
@RequiredArgsConstructor
public class CourseActivityGuard {

    private final CommonValidationHelper validationHelper;

    public void requireActive(TrainingCourse course) {
        if (course != null && Boolean.FALSE.equals(course.getStatus())) {
            throw validationHelper.conflict("Khóa huấn luyện đã bị vô hiệu hóa nên không thể thao tác. Hãy bật lại khóa trước.");
        }
    }
}
