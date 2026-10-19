package phucuong.ht.modules.training.courseParticipation.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class TrainingRegistrationRequest {

    // Danh sách đăng ký thường: chỉ gồm huynh trưởng đủ điều kiện. Có thể rỗng nếu chỉ gửi danh sách ngoại lệ.
    private List<UUID> leaderIds;

    // Không còn dùng: người nhận lấy từ danh sách tài khoản chọn khi tạo khóa huấn luyện.
    private List<String> recipientUsernames;

    // Danh sách ngoại lệ: huynh trưởng không đủ điều kiện -> lí do. Không được trùng với leaderIds.
    private Map<UUID, String> exceptionReasonsByLeaderId;
}
