package phucuong.ht.modules.training.trainingCourse.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Năm tổ chức quyết định mã chứng nhận nên bắt buộc khi tạo khóa; khi sửa bỏ trống = giữ nguyên.
class TrainingCourseHostYearValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequiresHostYear() {
        TrainingCourseCreateRequest request = validCreate();
        request.setHostYear(null);

        Set<ConstraintViolation<TrainingCourseCreateRequest>> violations = validator.validate(request);

        assertEquals(Set.of("Năm tổ chức là bắt buộc"), messagesOf(violations, "hostYear"));
    }

    @Test
    void createAcceptsAYearInTheSupportedRange() {
        TrainingCourseCreateRequest request = validCreate();
        request.setHostYear(2026);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void createRejectsAnOutOfRangeYear() {
        TrainingCourseCreateRequest tooEarly = validCreate();
        tooEarly.setHostYear(1899);
        TrainingCourseCreateRequest tooLate = validCreate();
        tooLate.setHostYear(2201);

        assertEquals(Set.of("Năm tổ chức không hợp lệ"), messagesOf(validator.validate(tooEarly), "hostYear"));
        assertEquals(Set.of("Năm tổ chức không hợp lệ"), messagesOf(validator.validate(tooLate), "hostYear"));
    }

    @Test
    void updateKeepsTheYearWhenItIsOmittedButRejectsAnOutOfRangeYear() {
        TrainingCourseUpdateRequest omitted = new TrainingCourseUpdateRequest();
        TrainingCourseUpdateRequest invalid = new TrainingCourseUpdateRequest();
        invalid.setHostYear(1000);
        TrainingCourseUpdateRequest valid = new TrainingCourseUpdateRequest();
        valid.setHostYear(2027);

        assertTrue(validator.validate(omitted).isEmpty());
        assertEquals(Set.of("Năm tổ chức không hợp lệ"), messagesOf(validator.validate(invalid), "hostYear"));
        assertTrue(validator.validate(valid).isEmpty());
    }

    private static TrainingCourseCreateRequest validCreate() {
        TrainingCourseCreateRequest request = new TrainingCourseCreateRequest();
        request.setCourseName("Khóa Huấn luyện");
        request.setCourseTypeCode("HTI");
        request.setPassingScore(BigDecimal.valueOf(5));
        return request;
    }

    private static <T> Set<String> messagesOf(Set<ConstraintViolation<T>> violations, String property) {
        return violations.stream()
                .filter(violation -> property.equals(violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
