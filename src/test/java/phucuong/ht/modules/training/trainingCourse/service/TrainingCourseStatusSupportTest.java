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

    private static final java.time.ZoneId VN = TrainingCourseStatusSupport.ZONE;

    private TrainingCourse timedCourse() {
        TrainingCourse course = new TrainingCourse();
        course.setStartDate(LocalDate.of(2026, 12, 10));
        course.setEndDate(LocalDate.of(2026, 12, 12));
        course.setStartTime(java.time.LocalTime.of(8, 0));
        course.setEndTime(java.time.LocalTime.of(17, 30));
        return course;
    }

    private static OffsetDateTime at(int day, int hour, int minute) {
        return java.time.ZonedDateTime.of(2026, 12, day, hour, minute, 0, 0, VN).toOffsetDateTime();
    }

    @Test
    void courseStartsAndEndsAtTheGivenTimeOfDay() {
        TrainingCourse course = timedCourse();
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.UPCOMING, TrainingCourseStatusSupport.organizingStatus(course, at(10, 7, 59)));
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.ONGOING, TrainingCourseStatusSupport.organizingStatus(course, at(10, 8, 0)));
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.ONGOING, TrainingCourseStatusSupport.organizingStatus(course, at(12, 17, 30)));
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.ENDED, TrainingCourseStatusSupport.organizingStatus(course, at(12, 17, 31)));
    }

    @Test
    void defaultTimesKeepWholeDaysAsBefore() {
        TrainingCourse course = new TrainingCourse();
        course.setStartDate(LocalDate.of(2026, 12, 10));
        course.setEndDate(LocalDate.of(2026, 12, 12));
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.ONGOING, TrainingCourseStatusSupport.organizingStatus(course, at(10, 0, 0)));
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.ONGOING, TrainingCourseStatusSupport.organizingStatus(course, at(12, 23, 59)));
        assertEquals(phucuong.ht.modules.training.enums.CourseOrganizingStatus.ENDED, TrainingCourseStatusSupport.organizingStatus(course, at(13, 0, 0)));
    }

    @Test
    void attendanceOpensTwoHoursBeforeStart() {
        TrainingCourse course = timedCourse();
        assertEquals(at(10, 6, 0), TrainingCourseStatusSupport.attendanceOpensAt(course));
        assertNull(TrainingCourseStatusSupport.attendanceOpensAt(new TrainingCourse()));
    }

    @Test
    void registrationClosesAtStartTimeNotAtMidnight() {
        TrainingCourse course = timedCourse();
        course.setRegistrationStartAt(at(1, 0, 0));
        course.setRegistrationEndAt(at(10, 7, 0));
        course.setRegistrationStatus(CourseRegistrationStatus.OPEN);
        assertEquals(CourseRegistrationStatus.OPEN, TrainingCourseStatusSupport.registrationStatus(course, at(10, 7, 0)));
        assertEquals(CourseRegistrationStatus.ENDED, TrainingCourseStatusSupport.registrationStatus(course, at(10, 8, 0)));
    }
}
