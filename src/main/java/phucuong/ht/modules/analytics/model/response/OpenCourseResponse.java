package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Khóa đang mở đăng ký. urgency: closing (còn tối đa 72 giờ) | open; hoursLeft rỗng nghĩa là không giới hạn hạn đăng ký. */
@Getter @Builder
public class OpenCourseResponse {
    private final String courseCode;
    private final String courseName;
    private final String courseType;
    private final String location;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final OffsetDateTime registrationEndAt;
    private final Long hoursLeft;
    private final String urgency;
    private final long registeredLeaders;
}
