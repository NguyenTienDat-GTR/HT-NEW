package phucuong.ht.modules.training.courseScoreComponent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent;
import phucuong.ht.modules.training.courseScoreComponent.model.request.ScoreComponentInput;
import phucuong.ht.modules.training.courseScoreComponent.repository.TrainingCourseScoreComponentRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseScoreComponentServiceTest {

    @Mock private TrainingCourseScoreComponentRepository repository;

    private CourseScoreComponentService service;

    @BeforeEach
    void setUp() {
        service = new CourseScoreComponentService(repository, new CommonValidationHelper(new securityUtils()));
    }

    @Test
    void newCourseCreatesItsOwnComponents() {
        TrainingCourse course = course("DT-2027");
        when(repository.findByCourseCode("DT-2027")).thenReturn(List.of());

        service.sync(course, List.of(input(null, "Tinh than", 10, 2, true), input(null, "Chuyen can", 10, 1, false)), "admin");

        ArgumentCaptor<TrainingCourseScoreComponent> saved = ArgumentCaptor.forClass(TrainingCourseScoreComponent.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals("Tinh than", saved.getAllValues().get(0).getName());
        assertEquals(0, saved.getAllValues().get(0).getDisplayOrder());
        assertEquals(1, saved.getAllValues().get(1).getDisplayOrder());
        assertFalse(saved.getAllValues().get(1).getIncludeInFinal());
    }

    @Test
    void rejectsEmptyDuplicateNameOrNoCountedComponent() {
        assertThrows(RuntimeException.class, () -> service.validateInputs(List.of()));
        assertThrows(RuntimeException.class, () -> service.validateInputs(List.of(input(null, "A", 10, 1, true), input(null, " a ", 10, 1, true))));
        assertThrows(RuntimeException.class, () -> service.validateInputs(List.of(input(null, "A", 10, 1, false))));
    }

    @Test
    void canRemoveComponentWhenNothingScoredYet() {
        TrainingCourse course = course("DT-2027");
        TrainingCourseScoreComponent a = existing("a-id", "A", 10, 2, true, 0);
        TrainingCourseScoreComponent b = existing("b-id", "B", 10, 1, true, 1);
        when(repository.findByCourseCode("DT-2027")).thenReturn(List.of(a, b));
        when(repository.findComponentIdsWithScoresInCourse("DT-2027")).thenReturn(Set.of());

        service.sync(course, List.of(input("b-id", "B", 10, 1, true)), "admin");

        verify(repository).remove(a);
        verify(repository, never()).remove(b);
    }

    @Test
    void cannotRemoveOrChangeWeightsOnceScoresExist() {
        TrainingCourse course = course("DT-2026");
        TrainingCourseScoreComponent a = existing("a-id", "A", 10, 2, true, 0);
        TrainingCourseScoreComponent b = existing("b-id", "B", 10, 1, true, 1);
        when(repository.findByCourseCode("DT-2026")).thenReturn(List.of(a, b));
        when(repository.findComponentIdsWithScoresInCourse("DT-2026")).thenReturn(Set.of("a-id"));

        assertThrows(RuntimeException.class, () -> service.sync(course, List.of(input("b-id", "B", 10, 1, true)), "admin"));
        assertThrows(RuntimeException.class, () -> service.sync(course,
                List.of(input("a-id", "A", 10, 3, true), input("b-id", "B", 10, 1, true)), "admin"));
        verify(repository, never()).remove(any());
    }

    @Test
    void canRenameAndReorderEvenWhenScored() {
        TrainingCourse course = course("DT-2026");
        TrainingCourseScoreComponent a = existing("a-id", "A", 10, 2, true, 0);
        TrainingCourseScoreComponent b = existing("b-id", "B", 10, 1, true, 1);
        when(repository.findByCourseCode("DT-2026")).thenReturn(List.of(a, b));

        service.sync(course, List.of(input("b-id", "B moi", 10, 1, true), input("a-id", "A", 10, 2, true)), "admin");

        assertEquals("B moi", b.getName());
        assertEquals(0, b.getDisplayOrder());
        assertEquals(1, a.getDisplayOrder());
        verify(repository, never()).findComponentIdsWithScoresInCourse(any());
    }

    @Test
    void componentFromAnotherCourseIsRejected() {
        TrainingCourse course = course("DT-2027");
        when(repository.findByCourseCode("DT-2027")).thenReturn(List.of(existing("a-id", "A", 10, 1, true, 0)));
        assertThrows(RuntimeException.class, () -> service.sync(course, List.of(input("other-course-id", "A", 10, 1, true)), "admin"));
    }

    @Test
    void lockedCourseCannotChangeComponents() {
        TrainingCourse course = course("DT-2027");
        course.setScoreLocked(true);
        TrainingCourseScoreComponent a = existing("a-id", "A", 10, 1, true, 0);
        when(repository.findByCourseCode("DT-2027")).thenReturn(List.of(a));
        assertThrows(RuntimeException.class, () -> service.sync(course, List.of(input("a-id", "A moi", 10, 1, true)), "admin"));
    }

    @Test
    void maxFinalScoreIsWeightedAverageOfCountedComponents() {
        // (10*2 + 5*1) / 3 = 8.33; thành phần không tính tổng kết bị bỏ qua.
        BigDecimal limit = CourseScoreComponentService.maxFinalOfInputs(List.of(
                input(null, "A", 10, 2, true), input(null, "B", 5, 1, true), input(null, "C", 100, 1, false)));
        assertEquals(0, new BigDecimal("8.33").compareTo(limit));
    }

    private ScoreComponentInput input(String id, String name, int max, int coefficient, boolean counted) {
        ScoreComponentInput input = new ScoreComponentInput();
        input.setId(id);
        input.setName(name);
        input.setMaxScore(BigDecimal.valueOf(max));
        input.setCoefficient(BigDecimal.valueOf(coefficient));
        input.setIncludeInFinal(counted);
        return input;
    }

    private TrainingCourseScoreComponent existing(String id, String name, int max, int coefficient, boolean counted, int order) {
        TrainingCourseScoreComponent item = new TrainingCourseScoreComponent();
        item.setId(id);
        item.setName(name);
        item.setMaxScore(BigDecimal.valueOf(max));
        item.setCoefficient(BigDecimal.valueOf(coefficient));
        item.setIncludeInFinal(counted);
        item.setDisplayOrder(order);
        return item;
    }

    private TrainingCourse course(String code) {
        TrainingCourse c = new TrainingCourse();
        c.setCourseCode(code);
        return c;
    }
}
