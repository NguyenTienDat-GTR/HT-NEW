package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;

// Tài khoản có thể được chọn làm người nhận danh sách đăng ký của khóa.
@Getter
@Builder
public class CourseRecipientCandidateResponse {
    private String username;
    private String fullName;
}
