package phucuong.ht.modules.training.courseScoreComponent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent;
import phucuong.ht.modules.training.courseScoreComponent.model.request.ScoreComponentInput;
import phucuong.ht.modules.training.courseScoreComponent.model.response.CourseScoreComponentResponse;
import phucuong.ht.modules.training.courseScoreComponent.repository.TrainingCourseScoreComponentRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

// Thành phần điểm do chính khóa khai báo khi tạo/sửa khóa (không có thư viện mẫu dùng chung).
// Điểm tổng kết = trung bình có hệ số của các thành phần có includeInFinal = true.
@Service
@RequiredArgsConstructor
public class CourseScoreComponentService {

    private final TrainingCourseScoreComponentRepository repository;
    private final CommonValidationHelper commonValidationHelper;

    /** Kiểm tra danh sách thành phần gửi lên: không rỗng, tên không trùng, có ít nhất một thành phần tính vào tổng kết. */
    public void validateInputs(List<ScoreComponentInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw commonValidationHelper.validationError("Khóa phải có ít nhất một thành phần điểm");
        }
        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        boolean anyCounted = false;
        for (ScoreComponentInput input : inputs) {
            String name = input.getName() == null ? "" : input.getName().trim();
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw commonValidationHelper.validationError("Tên thành phần điểm bị trùng: " + name);
            }
            if (input.getId() != null && !input.getId().isBlank() && !ids.add(input.getId())) {
                throw commonValidationHelper.validationError("Trùng thành phần điểm trong danh sách gửi lên");
            }
            if (!Boolean.FALSE.equals(input.getIncludeInFinal())) {
                anyCounted = true;
            }
        }
        if (!anyCounted) {
            throw commonValidationHelper.validationError("Phải có ít nhất một thành phần điểm được tính vào điểm tổng kết");
        }
    }

    /**
     * Đồng bộ thành phần của khóa với danh sách gửi lên (thứ tự danh sách = thứ tự hiển thị): có id thì cập nhật, không có id thì thêm,
     * thành phần của khóa vắng mặt trong danh sách thì xóa. Khi khóa đã có điểm, chỉ đổi được tên/mô tả/thứ tự; thêm, xóa, đổi điểm tối đa,
     * hệ số hay cờ tính tổng kết đều bị chặn để không làm lệch điểm tổng kết đã tính.
     */
    @Transactional
    public void sync(TrainingCourse course, List<ScoreComponentInput> inputs, String actor) {
        validateInputs(inputs);
        List<TrainingCourseScoreComponent> current = repository.findByCourseCode(course.getCourseCode());
        Map<String, TrainingCourseScoreComponent> currentById = new HashMap<>();
        current.forEach(item -> currentById.put(item.getId(), item));

        Set<String> keptIds = new HashSet<>();
        boolean changed = false;
        boolean structureChanged = false;
        List<Runnable> applies = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 0; index < inputs.size(); index++) {
            ScoreComponentInput input = inputs.get(index);
            int order = index;
            boolean counted = !Boolean.FALSE.equals(input.getIncludeInFinal());
            String name = input.getName().trim();
            String description = commonValidationHelper.normalizeOptionalText(input.getDescription());
            boolean hasId = input.getId() != null && !input.getId().isBlank();
            TrainingCourseScoreComponent existing = hasId ? currentById.get(input.getId()) : null;
            if (hasId && existing == null) {
                throw commonValidationHelper.validationError("Thành phần điểm không thuộc khóa huấn luyện này");
            }
            if (existing == null) {
                changed = true;
                structureChanged = true;
                applies.add(() -> {
                    TrainingCourseScoreComponent created = new TrainingCourseScoreComponent();
                    created.setCourse(course);
                    created.setName(name);
                    created.setDescription(description);
                    created.setMaxScore(input.getMaxScore());
                    created.setCoefficient(input.getCoefficient());
                    created.setIncludeInFinal(counted);
                    created.setDisplayOrder(order);
                    created.setStatus(Boolean.TRUE);
                    created.setCreatedAt(now);
                    created.setCreatedBy(actor);
                    created.setUpdatedAt(now);
                    created.setUpdatedBy(actor);
                    repository.save(created);
                });
                continue;
            }
            keptIds.add(existing.getId());
            boolean scoringChanged = existing.getMaxScore().compareTo(input.getMaxScore()) != 0
                    || existing.getCoefficient().compareTo(input.getCoefficient()) != 0
                    || Boolean.TRUE.equals(existing.getIncludeInFinal()) != counted;
            boolean cosmeticChanged = !Objects.equals(existing.getName(), name)
                    || !Objects.equals(existing.getDescription(), description)
                    || !Objects.equals(existing.getDisplayOrder(), order);
            if (scoringChanged) {
                structureChanged = true;
            }
            if (scoringChanged || cosmeticChanged) {
                changed = true;
                applies.add(() -> {
                    existing.setName(name);
                    existing.setDescription(description);
                    existing.setMaxScore(input.getMaxScore());
                    existing.setCoefficient(input.getCoefficient());
                    existing.setIncludeInFinal(counted);
                    existing.setDisplayOrder(order);
                    existing.setUpdatedAt(now);
                    existing.setUpdatedBy(actor);
                });
            }
        }
        List<TrainingCourseScoreComponent> removed = current.stream().filter(item -> !keptIds.contains(item.getId())).toList();
        if (!removed.isEmpty()) {
            changed = true;
            structureChanged = true;
        }
        if (!changed) {
            return;
        }
        requireEditable(course);
        if (structureChanged && !repository.findComponentIdsWithScoresInCourse(course.getCourseCode()).isEmpty()) {
            throw commonValidationHelper.conflict("Khóa đã có điểm được nhập nên không thể thêm, xóa thành phần hoặc đổi điểm tối đa, hệ số, cách tính tổng kết");
        }
        removed.forEach(repository::remove);
        applies.forEach(Runnable::run);
    }

    @Transactional(readOnly = true)
    public List<CourseScoreComponentResponse> list(String courseCode) {
        Set<String> scored = repository.findComponentIdsWithScoresInCourse(courseCode);
        return repository.findByCourseCode(courseCode).stream()
                .map(item -> CourseScoreComponentResponse.builder()
                        .id(item.getId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .maxScore(item.getMaxScore())
                        .coefficient(item.getCoefficient())
                        .includeInFinal(item.getIncludeInFinal())
                        .displayOrder(item.getDisplayOrder())
                        .hasScores(scored.contains(item.getId()))
                        .build())
                .toList();
    }

    /** Điểm tổng kết tối đa = trung bình có hệ số của điểm tối đa các thành phần được tính vào tổng kết (null nếu chưa có). */
    public static BigDecimal maxFinalOfInputs(List<ScoreComponentInput> inputs) {
        return maxFinal(inputs.stream()
                .filter(item -> !Boolean.FALSE.equals(item.getIncludeInFinal()))
                .map(item -> new BigDecimal[]{item.getMaxScore(), item.getCoefficient()})
                .toList());
    }

    public static BigDecimal maxFinalOfCourse(List<TrainingCourseScoreComponent> items) {
        return maxFinal(items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIncludeInFinal()))
                .map(item -> new BigDecimal[]{item.getMaxScore(), item.getCoefficient()})
                .toList());
    }

    private static BigDecimal maxFinal(Collection<BigDecimal[]> weights) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal coefficients = BigDecimal.ZERO;
        for (BigDecimal[] weight : weights) {
            weighted = weighted.add(weight[0].multiply(weight[1]));
            coefficients = coefficients.add(weight[1]);
        }
        return coefficients.signum() <= 0 ? null : weighted.divide(coefficients, 2, RoundingMode.HALF_UP);
    }

    private void requireEditable(TrainingCourse course) {
        if (course.getFinishedAt() != null) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã kết thúc nên không thể đổi thành phần điểm");
        }
        if (Boolean.TRUE.equals(course.getScoreLocked())) {
            throw commonValidationHelper.conflict("Điểm của khóa đang bị khóa, cần mở khóa trước khi đổi thành phần điểm");
        }
    }
}
