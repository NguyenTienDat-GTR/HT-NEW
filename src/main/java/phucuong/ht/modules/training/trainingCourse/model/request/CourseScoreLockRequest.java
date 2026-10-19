package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseScoreLockRequest {
    // true = khóa chỉnh sửa điểm, false = mở lại.
    @NotNull
    private Boolean locked;
}
