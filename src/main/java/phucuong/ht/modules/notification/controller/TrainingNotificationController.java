package phucuong.ht.modules.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.notification.model.response.TrainingNotificationResponse;
import phucuong.ht.modules.notification.service.TrainingNotificationService;

import java.util.List;

@RestController
@RequestMapping("/api/system/notifications")
@RequiredArgsConstructor
public class TrainingNotificationController {

    private final TrainingNotificationService trainingNotificationService;

    @GetMapping
    public apiResponse<List<TrainingNotificationResponse>> findMyNotifications(Authentication authentication) {
        return apiResponse.success("Lấy danh sách thông báo thành công", trainingNotificationService.findCurrentUserNotifications(authentication));
    }

    @PatchMapping("/{id}/read")
    public apiResponse<TrainingNotificationResponse> markRead(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Đánh dấu thông báo đã đọc thành công", trainingNotificationService.markRead(id, authentication));
    }
}
