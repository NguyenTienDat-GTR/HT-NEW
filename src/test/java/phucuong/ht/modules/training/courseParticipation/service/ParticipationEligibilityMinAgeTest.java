package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Tuổi tối thiểu của loại khóa: trống hoặc 0 là không yêu cầu, không đòi huynh trưởng phải có ngày sinh. */
class ParticipationEligibilityMinAgeTest {

    private final ParticipationEligibilityChecker checker = new ParticipationEligibilityChecker(
            new TrainingWorkflowSupport(), mock(RankHistoryRepository.class), mock(CourseParticipationRepository.class));

    @Test
    void zeroOrEmptyMinimumAgeDoesNotRequireABirthDate() {
        assertTrue(checker.isEligible("NONE", null, UUID.randomUUID(), courseWithMinAge(null)));
        assertTrue(checker.isEligible("NONE", null, UUID.randomUUID(), courseWithMinAge(0)));
    }

    @Test
    void positiveMinimumAgeStillNeedsAnOldEnoughLeader() {
        TrainingCourse course = courseWithMinAge(18);
        assertFalse(checker.isEligible("NONE", null, UUID.randomUUID(), course), "thiếu ngày sinh");
        assertFalse(checker.isEligible("NONE", LocalDate.now().minusYears(17), UUID.randomUUID(), course));
        assertTrue(checker.isEligible("NONE", LocalDate.now().minusYears(18), UUID.randomUUID(), course));
    }

    private static TrainingCourse courseWithMinAge(Integer minAge) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("T");
        type.setRequiredMinAge(minAge);
        TrainingCourse course = new TrainingCourse();
        course.setCourseType(type);
        return course;
    }
}
