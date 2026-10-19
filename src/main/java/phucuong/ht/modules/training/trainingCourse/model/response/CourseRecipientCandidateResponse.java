package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;

// Tài khoản có thể được chọn làm người nhận danh sách đăng ký của khóa.
@Getter
@Builder
public class CourseRecipientCandidateResponse {
    private String username;
    private String fullName;
    // Vai trò đang hiệu lực của tài khoản (nối bằng dấu phẩy, vai trò chính trước).
    private String roleName;
}
