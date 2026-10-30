package phucuong.ht.modules.training.courseParticipation.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.training.TrainingDecisionNotifier;
import phucuong.ht.modules.training.courseInvitation.service.CourseInvitationAutoTrigger;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;

import java.util.List;
import java.util.Map;

/**
 * Thông báo sau khi hồ sơ đăng ký được duyệt, bị từ chối hoặc bị xóa khỏi danh sách tham dự, và kích hoạt
 * gửi email thông tin tham dự khi khóa bật tự động. Các collaborator đều tùy chọn (thiếu thì bỏ qua).
 */
@Component
public class RegistrationDecisionNotifier {

    @Autowired(required = false)
    private TrainingDecisionNotifier decisionNotifier;
    @Autowired(required = false)
    private CourseInvitationAutoTrigger invitationAutoTrigger;

    /** Bản không làm gì, dùng khi dựng service bằng tay ngoài Spring. */
    public static RegistrationDecisionNotifier noop() {
        return new RegistrationDecisionNotifier();
    }

    public void decided(List<CourseParticipation> participations, boolean approved, Map<String, String> reviewsById, String actor) {
        // Khóa bật "tự gửi email thông tin tham dự": gửi cho hồ sơ vừa được duyệt (sau khi giao dịch duyệt commit).
        if (approved && invitationAutoTrigger != null) {
            invitationAutoTrigger.afterApproved(participations);
        }
        if (decisionNotifier == null || participations.isEmpty()) {
            return;
        }
        // Lý do chỉ có ý nghĩa với hồ sơ bị từ chối.
        Map<String, String> notes = approved ? null : reviewsById;
        decisionNotifier.notifyRegistrations(approved ? Decision.REGISTRATION_APPROVED : Decision.REGISTRATION_REJECTED,
                participations, notes, actor);
    }

    /** Người gửi đăng ký và admin giáo xứ của hồ sơ được báo hồ sơ đã bị xóa khỏi danh sách tham dự. */
    public void removed(CourseParticipation participation, String actor) {
        if (decisionNotifier != null) {
            decisionNotifier.notifyRegistrations(Decision.REGISTRATION_REMOVED, List.of(participation), null, actor);
        }
    }
}
