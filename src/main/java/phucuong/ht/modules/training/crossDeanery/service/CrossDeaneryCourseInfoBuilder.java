package phucuong.ht.modules.training.crossDeanery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CourseInfo;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.ArrayList;
import java.util.List;

// Tóm tắt khóa huấn luyện (loại khóa, địa điểm, thời gian, điều kiện, ghi chú) cho giáo hạt nhà xem khi xử lý liên giáo hạt.
@Component
@RequiredArgsConstructor
public class CrossDeaneryCourseInfoBuilder {

    private final LeaderLevelService leaderLevelService;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    public CourseInfo build(TrainingCourse course) {
        TrainingCourseType type = course.getCourseType();
        List<String> requirements = new ArrayList<>();
        if (type != null) {
            var levelOrder = leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));
            if (type.getRequiredCurrentLevel() != null && !type.getRequiredCurrentLevel().isBlank()) {
                requirements.add("Cấp độ hiện tại: " + levelOrder.nameOf(type.getRequiredCurrentLevel()));
            }
            if (type.getRequiredMinAge() != null) {
                requirements.add("Từ " + type.getRequiredMinAge() + " tuổi");
            }
            if (type.getRequiredYearsActive() != null) {
                requirements.add("Đã sinh hoạt tối thiểu " + type.getRequiredYearsActive() + " năm");
            }
            if (type.getPrerequisiteType() != null) {
                String within = type.getMaxYearsAfterPrerequisite() == null ? "" : " (trong vòng " + type.getMaxYearsAfterPrerequisite() + " năm)";
                requirements.add("Đã đạt khóa " + type.getPrerequisiteType().getTypeName() + within);
            }
        }
        return new CourseInfo(type == null ? null : type.getTypeName(), course.getLocation(), course.getStartDate(), course.getEndDate(),
                course.getRegistrationStartAt(), course.getRegistrationEndAt(), course.getAdditionalNotes(), requirements);
    }
}
