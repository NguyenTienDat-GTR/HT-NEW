package phucuong.ht.modules.training;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseActivityGuardTest {

    private UnitOperationalStatusService unitService;
    private CourseActivityGuard guard;
    private final UUID deaneryId = UUID.randomUUID();
    private final UUID dioceseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CommonValidationHelper helper = mock(CommonValidationHelper.class);
        when(helper.conflict(anyString()))
                .thenAnswer(invocation -> new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, invocation.getArgument(0)));
        unitService = mock(UnitOperationalStatusService.class);
        guard = new CourseActivityGuard(helper);
        ReflectionTestUtils.setField(guard, "unitOperationalStatusService", unitService);
    }

    private TrainingCourse deaneryCourse() {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        TrainingCourse course = new TrainingCourse();
        course.setStatus(Boolean.TRUE);
        course.setDeanery(deanery);
        return course;
    }

    @Test
    void lockedOrganizerFreezesCourse() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "khóa"))
                .when(unitService).requireUnitOperational(UnitType.DEANERY, deaneryId);

        assertThrows(ApiException.class, () -> guard.requireActive(deaneryCourse()));
    }

    @Test
    void revokePathIgnoresOrganizerLockButStillChecksCourseStatus() {
        assertDoesNotThrow(() -> guard.requireCourseEnabled(deaneryCourse()));
        verify(unitService, never()).requireUnitOperational(Mockito.any(), Mockito.any());

        TrainingCourse disabled = deaneryCourse();
        disabled.setStatus(Boolean.FALSE);
        assertThrows(ApiException.class, () -> guard.requireCourseEnabled(disabled));
    }

    @Test
    void dioceseOrganizedCourseChecksDiocese() {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        TrainingCourse course = new TrainingCourse();
        course.setStatus(Boolean.TRUE);
        course.setDiocese(diocese);

        guard.requireActive(course);

        verify(unitService).requireUnitOperational(UnitType.DIOCESE, dioceseId);
    }
}
