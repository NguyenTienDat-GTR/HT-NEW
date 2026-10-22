package phucuong.ht.modules.training;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseScoreVisibilityTest {

    private final CourseScoreVisibility visibility = new CourseScoreVisibility(null, null);

    @Test
    void scoresAreVisibleWhenCourseEnablesVisibility() {
        TrainingCourse course = new TrainingCourse();
        course.setScoreVisible(Boolean.TRUE);
        assertTrue(visibility.canSeeScores(course));
    }

    @Test
    void scoresAreVisibleOutsideARequestContext() {
        TrainingCourse course = new TrainingCourse();
        course.setScoreVisible(Boolean.FALSE);
        assertTrue(visibility.canSeeScores(course));
    }
}
