package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;

/** Kiểm tra huynh trưởng có đủ điều kiện (cấp độ, tuổi, khóa tiên quyết) để tham dự một khóa. */
@Component
@RequiredArgsConstructor
public class ParticipationEligibilityChecker {

    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final RankHistoryRepository rankHistoryRepository;
    private final CourseParticipationRepository courseParticipationRepository;

    // leaderId null: huynh trưởng chưa có hồ sơ (đang được import) nên chưa từng đạt khóa nào.
    public boolean isEligible(String leaderLevel, LocalDate birthDate, UUID leaderId, TrainingCourse course) {
        TrainingCourseType courseType = course.getCourseType();
        if (courseType != null) {
            if (courseType.getRequiredCurrentLevel() != null && !courseType.getRequiredCurrentLevel().equals(leaderLevel)) {
                return false;
            }
            if (courseType.getRequiredMinAge() != null) {
                if (birthDate == null) {
                    return false;
                }
                int age = Period.between(birthDate, LocalDate.now()).getYears();
                if (age < courseType.getRequiredMinAge()) {
                    return false;
                }
            }
        }

        return trainingWorkflowSupport.prerequisiteOf(course.getCourseType())
                .map(prerequisite -> leaderId != null && meetsPrerequisite(leaderId, course, prerequisite))
                .orElse(true);
    }

    // "Số năm hoạt động" = cách lần đạt được cấp độ hiện tại yêu cầu của loại khóa tối thiểu N năm: năm tổ chức khóa này - năm đạt cấp độ đó >= N.
    // Năm đạt lấy từ lịch sử cấp độ; huynh trưởng chưa có lịch sử cấp độ thì lấy năm đạt khóa tiên quyết gần nhất (duyệt chứng nhận / năm tổ chức khóa).
    // Ví dụ khóa cấp 2 tổ chức 2026 yêu cầu 2 năm: lần đạt cấp 1 gần nhất phải từ 2024 trở về trước.
    // Thời hạn tối đa sau khóa tiên quyết vẫn tính từ năm đạt khóa tiên quyết gần nhất (không theo lịch sử cấp độ).
    private boolean meetsPrerequisite(UUID leaderId, TrainingCourse course, TrainingCourseType prerequisite) {
        TrainingCourseType courseType = course.getCourseType();
        String requiredLevel = courseType == null ? null : courseType.getRequiredCurrentLevel();
        Optional<Integer> achievedYear = requiredLevel == null ? Optional.empty() : rankHistoryRepository.findLatestYearReachedLevel(leaderId, requiredLevel);
        if (achievedYear.isEmpty()) {
            achievedYear = courseParticipationRepository.findLatestCompletedYearByLeaderAndCourseType(leaderId, prerequisite.getTypeCode());
        }
        int requiredYears = course.getYearsActive() == null ? 0 : course.getYearsActive();
        Integer maxYears = courseType == null ? null : courseType.getMaxYearsAfterPrerequisite();
        if (achievedYear.isEmpty()) {
            // Không xác định được năm đạt: chỉ chấp nhận khi khóa không đặt ràng buộc về số năm.
            return requiredLevel != null && requiredYears == 0 && maxYears == null;
        }
        if (resolveHostYear(course) - achievedYear.get() < requiredYears) {
            return false;
        }
        if (maxYears == null) {
            return true;
        }
        // Giới hạn thời hạn: phải đi khóa này trong vòng N năm kể từ lần đạt KHÓA TIÊN QUYẾT gần nhất.
        // Ví dụ đạt khóa tiên quyết 2022, khóa 2026 tối đa 3 năm: 2026 - 2022 = 4 > 3 nên không đủ điều kiện (phải đạt từ 2023).
        return courseParticipationRepository.findLatestCompletedYearByLeaderAndCourseType(leaderId, prerequisite.getTypeCode())
                .map(year -> resolveHostYear(course) - year <= maxYears)
                .orElse(false);
    }

    private int resolveHostYear(TrainingCourse course) {
        if (course.getHostYear() != null) {
            return course.getHostYear();
        }
        if (course.getStartDate() != null) {
            return course.getStartDate().getYear();
        }
        return LocalDate.now().getYear();
    }
}
