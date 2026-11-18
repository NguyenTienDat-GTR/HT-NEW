package phucuong.ht.modules.training.trainingCourse.service;

import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// Tính trạng thái đăng ký / tổ chức của khóa theo thời gian hiện tại (ngày + giờ bắt đầu/kết thúc, múi giờ Việt Nam).
public final class TrainingCourseStatusSupport {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Giờ kết thúc mặc định khi khóa không ghi giờ: hết ngày.
    public static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    // Được điểm danh / quét QR sớm hơn giờ bắt đầu khóa tối đa chừng này.
    public static final Duration ATTENDANCE_EARLY = Duration.ofHours(2);

    private TrainingCourseStatusSupport() {
    }

    // Đóng thủ công (CLOSED) được giữ nguyên cho tới khi mở thủ công hoặc hết hạn đăng ký.
    public static CourseRegistrationStatus registrationStatus(TrainingCourse course, OffsetDateTime now) {
        // Khóa quá khứ không có thời gian đăng ký: luôn đã kết thúc đăng ký.
        if (course.isPast()) {
            return CourseRegistrationStatus.ENDED;
        }
        // Khóa đã diễn ra (đã tới ngày bắt đầu) hoặc đã kết thúc thì bắt buộc đã đóng đăng ký, kể cả khi bật cờ mở đăng ký, không mở lại được.
        if (isCourseStarted(course, now)) {
            return CourseRegistrationStatus.ENDED;
        }
        // Khóa không mở đăng ký: luôn đóng, không ai đăng ký được.
        if (Boolean.FALSE.equals(course.getRegistrationEnabled())) {
            return CourseRegistrationStatus.CLOSED;
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

    // Đã tới giờ bắt đầu (đang diễn ra) hoặc đã kết thúc.
    public static boolean isCourseStarted(TrainingCourse course, OffsetDateTime now) {
        return course != null && organizingStatus(course, now) != CourseOrganizingStatus.UPCOMING;
    }

    public static boolean isCourseEnded(TrainingCourse course, OffsetDateTime now) {
        return organizingStatus(course, now) == CourseOrganizingStatus.ENDED;
    }

    // Thời điểm bắt đầu: ngày bắt đầu kèm giờ bắt đầu (giờ Việt Nam); null nếu khóa chưa có ngày bắt đầu.
    public static OffsetDateTime startAt(TrainingCourse course) {
        return moment(course.getStartDate(), course.getStartTime(), LocalTime.MIN);
    }

    // Thời điểm kết thúc: ngày kết thúc kèm giờ kết thúc (giờ Việt Nam); null nếu khóa chưa có ngày kết thúc.
    public static OffsetDateTime endAt(TrainingCourse course) {
        return moment(course.getEndDate(), course.getEndTime(), END_OF_DAY);
    }

    // Từ lúc này khóa được điểm danh / quét QR: sớm hơn giờ bắt đầu 2 tiếng; null nếu khóa chưa có ngày bắt đầu.
    public static OffsetDateTime attendanceOpensAt(TrainingCourse course) {
        OffsetDateTime start = startAt(course);
        return start == null ? null : start.minus(ATTENDANCE_EARLY);
    }

    private static OffsetDateTime moment(LocalDate date, LocalTime time, LocalTime fallback) {
        if (date == null) {
            return null;
        }
        return date.atTime(time == null ? fallback : time).atZone(ZONE).toOffsetDateTime();
    }

    public static CourseOrganizingStatus organizingStatus(TrainingCourse course, OffsetDateTime now) {
        // Đã kết thúc thủ công thì luôn là đã kết thúc, kể cả trước giờ kết thúc dự kiến.
        if (course.getFinishedAt() != null) {
            return CourseOrganizingStatus.ENDED;
        }
        OffsetDateTime start = startAt(course);
        OffsetDateTime end = endAt(course);
        // Giờ kết thúc tính tới hết phút đó (23:59 là hết ngày).
        if (end != null && now.truncatedTo(ChronoUnit.MINUTES).isAfter(end)) {
            return CourseOrganizingStatus.ENDED;
        }
        if (start == null || now.isBefore(start)) {
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
        CourseOrganizingStatus organizing = organizingStatus(course, now);
        boolean changed = registration != course.getRegistrationStatus() || organizing != course.getOrganizingStatus();
        course.setRegistrationStatus(registration);
        course.setOrganizingStatus(organizing);
        return changed;
    }
}
