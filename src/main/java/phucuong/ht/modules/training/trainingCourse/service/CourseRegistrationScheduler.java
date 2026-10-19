package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Định kỳ tự đóng đăng ký các khóa đã hết thời gian đăng ký.
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseRegistrationScheduler {

    private final TrainingCourseService trainingCourseService;

    @Scheduled(fixedDelayString = "${training.registration.auto-close-interval-ms:60000}", initialDelayString = "${training.registration.auto-close-interval-ms:60000}")
    public void closeExpiredRegistrations() {
        try {
            int closed = trainingCourseService.closeExpiredRegistrations();
            if (closed > 0) {
                log.info("Đã tự động đóng đăng ký {} khóa huấn luyện hết hạn", closed);
            }
        } catch (RuntimeException ex) {
            log.warn("Không thể tự động đóng đăng ký khóa huấn luyện hết hạn", ex);
        }
    }
}
