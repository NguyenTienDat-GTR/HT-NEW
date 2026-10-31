package phucuong.ht.modules.training;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseActivityGuardTest {

    private final CommonValidationHelper helper = mock(CommonValidationHelper.class);
    private final CourseActivityGuard guard = new CourseActivityGuard(helper);

    CourseActivityGuardTest() {
        when(helper.conflict(anyString())).thenReturn(mock(ApiException.class));
    }

    private TrainingCourse course(LocalDate start, LocalDate end) {
        TrainingCourse course = new TrainingCourse();
        course.setStartDate(start);
        course.setEndDate(end);
        return course;
    }

    @Test
    void courseIsEndedAfterEndDate() {
        TrainingCourse course = course(LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        assertTrue(guard.isEnded(course));
        assertThrows(Throwable.class, () -> guard.requireNotEnded(course));
    }

    @Test
    void courseIsEndedWhenFinishedManually() {
        TrainingCourse course = course(LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));
        course.setFinishedAt(OffsetDateTime.now());
        assertTrue(guard.isEnded(course));
    }

    @Test
    void ongoingCourseIsNotEnded() {
        TrainingCourse course = course(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertFalse(guard.isEnded(course));
        assertDoesNotThrow(() -> guard.requireNotEnded(course));
    }
}
