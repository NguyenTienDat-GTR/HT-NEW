package phucuong.ht.modules.training.trainingCourse.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCourseStatusSupportTest {

    private final OffsetDateTime now = OffsetDateTime.now();

    private TrainingCourse openCourse() {
        TrainingCourse course = new TrainingCourse();
        course.setStartDate(LocalDate.now().minusDays(2));
        course.setEndDate(LocalDate.now().plusDays(5));
        course.setRegistrationStartAt(now.minusDays(10));
        course.setRegistrationEndAt(now.plusDays(10));
        course.setRegistrationStatus(CourseRegistrationStatus.OPEN);
        return course;
    }

    @Test
    void upcomingCourseKeepsRegistrationOpenWhileTheWindowIsOpen() {
        TrainingCourse course = openCourse();
        course.setStartDate(LocalDate.now().plusDays(2));
        course.setEndDate(LocalDate.now().plusDays(5));
        assertEquals(CourseRegistrationStatus.OPEN, TrainingCourseStatusSupport.registrationStatus(course, now));
    }

    @Test
    void courseThatHasStartedClosesRegistrationEvenWithTheFlagOnAndTheWindowOpen() {
        TrainingCourse course = openCourse();
        course.setRegistrationEnabled(Boolean.TRUE);
        assertTrue(TrainingCourseStatusSupport.isCourseStarted(course, now));
        assertEquals(CourseRegistrationStatus.ENDED, TrainingCourseStatusSupport.registrationStatus(course, now));
        course.setStartDate(LocalDate.now());
        assertEquals(CourseRegistrationStatus.ENDED, TrainingCourseStatusSupport.registrationStatus(course, now));
    }

    @Test
    void manuallyFinishedCourseMustHaveRegistrationClosed() {
        TrainingCourse course = openCourse();
        course.setFinishedAt(now);
        assertTrue(TrainingCourseStatusSupport.isCourseEnded(course, now));
        assertEquals(CourseRegistrationStatus.ENDED, TrainingCourseStatusSupport.registrationStatus(course, now));
    }

    @Test
    void courseAfterEndDateMustHaveRegistrationClosedEvenIfWindowStillOpen() {
        TrainingCourse course = openCourse();
        course.setEndDate(LocalDate.now().minusDays(1));
        assertEquals(CourseRegistrationStatus.ENDED, TrainingCourseStatusSupport.registrationStatus(course, now));
        TrainingCourseStatusSupport.refresh(course, now);
        assertEquals(CourseRegistrationStatus.ENDED, course.getRegistrationStatus());
    }

    @Test
    void extendingRegistrationWindowCannotReopenEndedCourse() {
        TrainingCourse course = openCourse();
        course.setFinishedAt(now);
        course.setRegistrationEndAt(now.plusDays(30));
        TrainingCourseStatusSupport.refresh(course, now);
        assertEquals(CourseRegistrationStatus.ENDED, course.getRegistrationStatus());
    }
}
