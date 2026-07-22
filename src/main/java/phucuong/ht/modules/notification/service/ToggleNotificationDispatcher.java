package phucuong.ht.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ToggleNotificationDispatcher {

    private final TrainingNotificationService trainingNotificationService;

    public void dispatch(Collection<String> recipients, String eventType, String targetLabel, boolean enabled, String actor) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        String normalizedActor = actor == null || actor.isBlank() ? "hệ thống" : actor;
        trainingNotificationService.notifyUsers(List.copyOf(recipients), TrainingNotificationCommand.builder()
                .eventType(eventType)
                .title(title(eventType, enabled))
                .message(message(eventType, targetLabel, enabled, normalizedActor))
                .actor(actor)
                .build());
    }

    private String title(String eventType, boolean enabled) {
        return switch (eventType) {
            case "ROLE_TOGGLED" -> enabled ? "Vai trò của bạn đã được kích hoạt lại" : "Vai trò của bạn đã bị vô hiệu hóa";
            case "PERMISSION_TOGGLED" -> enabled ? "Quyền truy cập của bạn đã được kích hoạt lại" : "Quyền truy cập của bạn đã bị vô hiệu hóa";
            case "ACCOUNT_ROLE_TOGGLED" -> enabled ? "Vai trò gắn với tài khoản của bạn đã được kích hoạt lại" : "Vai trò gắn với tài khoản của bạn đã bị vô hiệu hóa";
            case "ROLE_PERMISSION_TOGGLED" -> enabled ? "Quyền trong vai trò của bạn đã được cấp lại" : "Quyền trong vai trò của bạn đã bị thu hồi";
            case "ACCOUNT_PERMISSION_TOGGLED" -> enabled ? "Quyền riêng của tài khoản bạn đã được cho phép" : "Quyền riêng của tài khoản bạn đã bị từ chối";
            case "UNIT_STATUS_TOGGLED" -> enabled ? "Đơn vị của bạn đã được mở lại" : "Đơn vị của bạn đã bị tạm ngưng";
            default -> "Có cập nhật liên quan đến tài khoản của bạn";
        };
    }

    private String message(String eventType, String targetLabel, boolean enabled, String actor) {
        String target = targetLabel == null || targetLabel.isBlank() ? "mục liên quan" : targetLabel;
        return switch (eventType) {
            case "ROLE_TOGGLED" -> enabled
                    ? "Vai trò " + target + " mà tài khoản của bạn đang sử dụng đã được kích hoạt lại bởi " + actor + ". Bạn có thể sử dụng lại các chức năng thuộc vai trò này nếu các quyền liên quan còn hiệu lực."
                    : "Vai trò " + target + " mà tài khoản của bạn đang sử dụng đã bị vô hiệu hóa bởi " + actor + ". Một số màn hình hoặc thao tác có thể không còn khả dụng.";
            case "PERMISSION_TOGGLED" -> enabled
                    ? "Quyền " + target + " liên quan đến tài khoản của bạn đã được kích hoạt lại bởi " + actor + ". Các chức năng sử dụng quyền này có thể hoạt động trở lại nếu vai trò của bạn còn hiệu lực."
                    : "Quyền " + target + " liên quan đến tài khoản của bạn đã bị vô hiệu hóa bởi " + actor + ". Bạn có thể không còn thực hiện được một số thao tác tương ứng.";
            case "ACCOUNT_ROLE_TOGGLED" -> enabled
                    ? "Vai trò " + target + " gắn với tài khoản của bạn đã được kích hoạt lại bởi " + actor + ". Quyền truy cập từ vai trò này có thể được khôi phục."
                    : "Vai trò " + target + " gắn với tài khoản của bạn đã bị vô hiệu hóa bởi " + actor + ". Bạn có thể mất quyền truy cập vào các chức năng thuộc vai trò này.";
            case "ROLE_PERMISSION_TOGGLED" -> enabled
                    ? "Quyền " + target + " đã được cấp lại bởi " + actor + ". Quyền truy cập của bạn có thể được cập nhật sau khi hệ thống đồng bộ."
                    : "Quyền " + target + " đã bị thu hồi bởi " + actor + ". Một số thao tác liên quan có thể không còn khả dụng.";
            case "ACCOUNT_PERMISSION_TOGGLED" -> enabled
                    ? "Quyền riêng " + target + " trên tài khoản của bạn đã được cho phép bởi " + actor + ". Bạn có thể sử dụng chức năng tương ứng nếu không còn giới hạn khác."
                    : "Quyền riêng " + target + " trên tài khoản của bạn đã bị từ chối bởi " + actor + ". Bạn sẽ không thể thực hiện thao tác tương ứng cho đến khi quyền này được cho phép lại.";
            case "UNIT_STATUS_TOGGLED" -> enabled
                    ? target + " của bạn đã được mở lại bởi " + actor + ". Bạn có thể sử dụng lại các chức năng nếu tài khoản, vai trò và quyền của bạn còn hiệu lực."
                    : target + " của bạn đã bị tạm ngưng bởi " + actor + ". Trong thời gian đơn vị bị tạm ngưng, bạn chỉ có thể đăng nhập và đọc thông báo.";
            default -> "Có cập nhật liên quan đến " + target + " bởi " + actor + ". Vui lòng tải lại trang nếu quyền truy cập của bạn chưa đồng bộ.";
        };
    }
}
