package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Định kỳ tự mở/đóng đăng ký theo thời gian đăng ký và chuyển trạng thái tổ chức của khóa.
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseRegistrationScheduler {

    private final TrainingCourseService trainingCourseService;

    @Scheduled(fixedDelayString = "${training.registration.auto-close-interval-ms:60000}", initialDelayString = "${training.registration.auto-close-interval-ms:60000}")
    public void refreshCourseStatuses() {
        try {
            int changed = trainingCourseService.refreshCourseStatuses();
            if (changed > 0) {
                log.info("Đã tự động cập nhật trạng thái đăng ký/tổ chức của {} khóa huấn luyện", changed);
            }
        } catch (RuntimeException ex) {
            log.warn("Không thể tự động cập nhật trạng thái khóa huấn luyện", ex);
        }
    }
}
