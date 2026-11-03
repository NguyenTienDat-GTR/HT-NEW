package phucuong.ht.modules.training.courseParticipation.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class TrainingRegistrationRequest {

    // Tư cách gửi (DIOCESE | DEANERY | PARISH) khi tài khoản có nhiều tư cách; bỏ trống thì hệ thống chọn tư cách phù hợp với khóa.
    private phucuong.ht.common.enums.UnitType senderType;

    // Danh sách đăng ký thường: chỉ gồm huynh trưởng đủ điều kiện. Có thể rỗng nếu chỉ gửi danh sách ngoại lệ.
    private List<UUID> leaderIds;

    // Không còn dùng: người nhận lấy từ danh sách tài khoản chọn khi tạo khóa huấn luyện.
    private List<String> recipientUsernames;

    // Danh sách ngoại lệ: huynh trưởng không đủ điều kiện -> lí do. Không được trùng với leaderIds.
    private Map<UUID, String> exceptionReasonsByLeaderId;

    // Lí do riêng cho từng hồ sơ gửi ngoài đợt của khóa giáo phận dành cho một số giáo hạt (bắt buộc với hồ sơ ngoài đợt).
    private Map<UUID, String> offTargetReasonsByLeaderId;

    // Ghi chú tùy chọn của người gửi cho từng hồ sơ (tối đa 1000 ký tự).
    private Map<UUID, String> notesByLeaderId;
}
