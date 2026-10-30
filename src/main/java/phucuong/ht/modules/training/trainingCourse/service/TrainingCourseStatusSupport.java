package phucuong.ht.modules.training.trainingCourse.service;

import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// Tính trạng thái đăng ký / tổ chức của khóa theo thời gian hiện tại.
public final class TrainingCourseStatusSupport {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private TrainingCourseStatusSupport() {
    }

    // Đóng thủ công (CLOSED) được giữ nguyên cho tới khi mở thủ công hoặc hết hạn đăng ký.
    public static CourseRegistrationStatus registrationStatus(TrainingCourse course, OffsetDateTime now) {
        // Khóa quá khứ không có thời gian đăng ký: luôn đã kết thúc đăng ký.
        if (course.isPast()) {
            return CourseRegistrationStatus.ENDED;
        }
        // Khóa đã kết thúc (kết thúc thủ công hoặc quá ngày kết thúc) thì bắt buộc đã đóng đăng ký, không mở lại được.
        if (isCourseEnded(course, now)) {
            return CourseRegistrationStatus.ENDED;
        }
        OffsetDateTime start = course.getRegistrationStartAt();
        OffsetDateTime end = course.getRegistrationEndAt();
        if (end != null && now.isAfter(end)) {
            return CourseRegistrationStatus.ENDED;
        }
        if (course.getRegistrationStatus() == CourseRegistrationStatus.CLOSED) {
            return CourseRegistrationStatus.CLOSED;
        }
        if (start == null || end == null || now.isBefore(start)) {
            return CourseRegistrationStatus.NOT_STARTED;
        }
        return CourseRegistrationStatus.OPEN;
    }

    public static boolean isCourseEnded(TrainingCourse course, OffsetDateTime now) {
        return organizingStatus(course, now.atZoneSameInstant(ZONE).toLocalDate()) == CourseOrganizingStatus.ENDED;
    }

    public static CourseOrganizingStatus organizingStatus(TrainingCourse course, LocalDate today) {
        // Đã kết thúc thủ công thì luôn là đã kết thúc, kể cả trước ngày kết thúc dự kiến.
        if (course.getFinishedAt() != null) {
            return CourseOrganizingStatus.ENDED;
        }
        LocalDate start = course.getStartDate();
        LocalDate end = course.getEndDate();
        if (end != null && today.isAfter(end)) {
            return CourseOrganizingStatus.ENDED;
        }
        if (start == null || today.isBefore(start)) {
            return CourseOrganizingStatus.UPCOMING;
        }
        return CourseOrganizingStatus.ONGOING;
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // Cập nhật cả hai trạng thái; trả về true nếu có thay đổi.
    public static boolean refresh(TrainingCourse course, OffsetDateTime now) {
        CourseRegistrationStatus registration = registrationStatus(course, now);
        CourseOrganizingStatus organizing = organizingStatus(course, now.atZoneSameInstant(ZONE).toLocalDate());
        boolean changed = registration != course.getRegistrationStatus() || organizing != course.getOrganizingStatus();
        course.setRegistrationStatus(registration);
        course.setOrganizingStatus(organizing);
        return changed;
    }
}
