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

        Optional<TrainingCourseType> prerequisite = trainingWorkflowSupport.prerequisiteOf(course.getCourseType());
        if (requiredYearsActive(course) > 0 && (leaderId == null || !meetsActiveYears(leaderId, course, prerequisite.orElse(null)))) {
            return false;
        }
        return prerequisite
                .map(prerequisiteType -> leaderId != null && meetsPrerequisite(leaderId, course, prerequisiteType))
                .orElse(true);
    }

    // Số năm hoạt động yêu cầu do từng khóa đặt (loại khóa không còn cấu hình số năm).
    private int requiredYearsActive(TrainingCourse course) {
        return course.getYearsActive() == null ? 0 : course.getYearsActive();
    }

    // Đủ số năm sinh hoạt khi MỘT trong hai mốc cách năm tổ chức khóa >= N năm (làm tròn theo năm):
    // năm đạt cấp độ yêu cầu (lịch sử cấp độ) hoặc năm đạt khóa tiên quyết gần nhất (kể cả khóa trong quá khứ).
    // Ví dụ khóa cấp 2 tổ chức 2026 yêu cầu 2 năm: đạt cấp 1 / khóa cấp 1 từ 2024 trở về trước.
    private boolean meetsActiveYears(UUID leaderId, TrainingCourse course, TrainingCourseType prerequisite) {
        TrainingCourseType courseType = course.getCourseType();
        String requiredLevel = courseType == null ? null : courseType.getRequiredCurrentLevel();
        int hostYear = resolveHostYear(course);
        int requiredYears = requiredYearsActive(course);
        boolean byLevel = requiredLevel != null
                && rankHistoryRepository.findLatestYearReachedLevel(leaderId, requiredLevel).map(year -> hostYear - year >= requiredYears).orElse(false);
        if (byLevel) {
            return true;
        }
        return prerequisite != null
                && courseParticipationRepository.findLatestCompletedYearByLeaderAndCourseType(leaderId, prerequisite.getTypeCode())
                        .map(year -> hostYear - year >= requiredYears)
                        .orElse(false);
    }

    // Số năm sinh hoạt đã kiểm tra riêng ở meetsActiveYears.
    // Thời hạn tối đa sau khóa tiên quyết vẫn tính từ năm đạt khóa tiên quyết gần nhất (không theo lịch sử cấp độ).
    private boolean meetsPrerequisite(UUID leaderId, TrainingCourse course, TrainingCourseType prerequisite) {
        TrainingCourseType courseType = course.getCourseType();
        String requiredLevel = courseType == null ? null : courseType.getRequiredCurrentLevel();
        Optional<Integer> achievedYear = requiredLevel == null ? Optional.empty() : rankHistoryRepository.findLatestYearReachedLevel(leaderId, requiredLevel);
        if (achievedYear.isEmpty()) {
            achievedYear = courseParticipationRepository.findLatestCompletedYearByLeaderAndCourseType(leaderId, prerequisite.getTypeCode());
        }
        Integer maxYears = courseType == null ? null : courseType.getMaxYearsAfterPrerequisite();
        if (achievedYear.isEmpty()) {
            // Không xác định được năm đạt: chỉ chấp nhận khi khóa không đặt ràng buộc về số năm.
            return requiredLevel != null && requiredYearsActive(course) == 0 && maxYears == null;
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
