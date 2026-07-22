package phucuong.ht.modules.notification.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnreadNotificationCountResponse {
    private final long unreadCount;
}
