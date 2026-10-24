package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Dùng chung cho thẻ tham dự, điểm danh và email thông tin tham dự: lấy khóa thường và danh sách tham dự của khóa.
@Component
@RequiredArgsConstructor
public class CourseParticipantSupport {

    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final CommonValidationHelper commonValidationHelper;

    // Khóa thường (không phải khóa quá khứ): chỉ khóa thường có thẻ, điểm danh và email thông tin tham dự.
    public TrainingCourse requireRegularCourse(String courseCode, String featureName) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        if (course.isPast()) {
            throw commonValidationHelper.validationError("Khóa quá khứ không áp dụng " + featureName);
        }
        return course;
    }

    // Hồ sơ nằm trong danh sách tham dự: đã duyệt đăng ký, chưa bị từ chối / loại và còn hiệu lực.
    public List<CourseParticipation> participantsOf(String courseCode) {
        return courseParticipationRepository.findAllByCourseCode(courseCode).stream()
                .filter(CourseParticipantSupport::isParticipant)
                .toList();
    }

    public static boolean isParticipant(CourseParticipation participation) {
        return participation.getApprovalStatus() == WorkflowApprovalStatus.APPROVED
                && participation.getParticipationStatus() != ParticipationStatus.REJECTED
                && !Boolean.FALSE.equals(participation.getStatus())
                && participation.getLeader() != null;
    }

    // Đội (tên tự nhiên) -> họ và tên -> tên thánh, hồ sơ chưa có đội xếp sau cùng.
    public static Comparator<CourseParticipation> byTeamThenName() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        return Comparator
                .comparing((CourseParticipation p) -> p.getTeamName() == null || p.getTeamName().isBlank() ? 1 : 0)
                .thenComparing(p -> p.getTeamName() == null ? "" : p.getTeamName(), CourseParticipantSupport::naturalCompare)
                .thenComparing(p -> sortName(p.getLeader()), collator)
                .thenComparing(p -> p.getLeader().getHolyName() == null ? "" : p.getLeader().getHolyName(), collator);
    }

    // "Đội 2" < "Đội 10": so sánh theo cụm chữ số.
    static int naturalCompare(String a, String b) {
        String[] left = a.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] right = b.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean numeric = left[i].matches("\\d+") && right[i].matches("\\d+");
            int cmp = numeric
                    ? new java.math.BigInteger(left[i]).compareTo(new java.math.BigInteger(right[i]))
                    : left[i].compareToIgnoreCase(right[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    // Họ và tên theo thứ tự "Tên Họ" để sắp xếp theo tên trước (cách xếp danh sách thông thường ở Việt Nam).
    private static String sortName(Leader leader) {
        String first = leader.getFirstName() == null ? "" : leader.getFirstName().trim();
        String last = leader.getLastName() == null ? "" : leader.getLastName().trim();
        return first + " " + last;
    }

    // Tên thánh + họ và tên (vd "Giuse Nguyễn Văn A").
    public static String fullName(Leader leader) {
        if (leader == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, leader.getHolyName());
        append(sb, leader.getLastName());
        append(sb, leader.getFirstName());
        return sb.toString();
    }

    // Họ và tên không kèm tên thánh.
    public static String plainName(Leader leader) {
        if (leader == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, leader.getLastName());
        append(sb, leader.getFirstName());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
    }
}
