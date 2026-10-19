package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseRegistrationToggleRequest {

    // true = mở đăng ký, false = đóng đăng ký.
    @NotNull
    private Boolean open;
}
