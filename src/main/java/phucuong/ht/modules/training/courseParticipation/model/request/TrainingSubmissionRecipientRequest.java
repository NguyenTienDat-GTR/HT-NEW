package phucuong.ht.modules.training.courseParticipation.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TrainingSubmissionRecipientRequest {
    // Không còn dùng: người nhận lấy từ danh sách tài khoản chọn khi tạo khóa huấn luyện.
    private List<String> recipientUsernames;
}
