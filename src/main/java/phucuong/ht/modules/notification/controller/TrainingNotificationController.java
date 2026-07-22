package phucuong.ht.modules.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.notification.model.response.TrainingNotificationResponse;
import phucuong.ht.modules.notification.model.response.UnreadNotificationCountResponse;
import phucuong.ht.modules.notification.service.TrainingNotificationService;

@RestController
@RequestMapping("/api/system/notifications")
@RequiredArgsConstructor
public class TrainingNotificationController {

    private final TrainingNotificationService trainingNotificationService;

    @GetMapping
    public apiResponse<Page<TrainingNotificationResponse>> findMyNotifications(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách thông báo thành công", trainingNotificationService.findCurrentUserNotifications(request, authentication));
    }

    @GetMapping("/unread-count")
    public apiResponse<UnreadNotificationCountResponse> unreadCount(Authentication authentication) {
        return apiResponse.success("Lấy số thông báo chưa đọc thành công", trainingNotificationService.unreadCount(authentication));
    }

    @PatchMapping("/{id}/read")
    public apiResponse<TrainingNotificationResponse> markRead(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Đánh dấu thông báo đã đọc thành công", trainingNotificationService.markRead(id, authentication));
    }
}
