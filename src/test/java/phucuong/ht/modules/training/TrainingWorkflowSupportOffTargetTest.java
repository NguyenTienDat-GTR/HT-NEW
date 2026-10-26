package phucuong.ht.modules.training;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingWorkflowSupportOffTargetTest {

    private final TrainingWorkflowSupport support = new TrainingWorkflowSupport();
    private final UUID targetDeanery = UUID.randomUUID();

    private TrainingCourse course(UnitType managementUnit, UUID... targets) {
        TrainingCourseType type = new TrainingCourseType();
        type.setManagementUnitType(managementUnit);
        TrainingCourse course = new TrainingCourse();
        course.setCourseType(type);
        course.getTargetDeaneryIds().addAll(java.util.List.of(targets));
        return course;
    }

    private Leader leaderIn(UUID deaneryId) {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Parish parish = new Parish();
        parish.setDeanery(deanery);
        Leader leader = new Leader();
        leader.setParish(parish);
        return leader;
    }

    @Test
    void leaderOutsideTargetDeaneriesIsOffTarget() {
        TrainingCourse course = course(UnitType.DIOCESE, targetDeanery);
        assertTrue(support.isOffTarget(course, leaderIn(UUID.randomUUID())));
        assertFalse(support.isOffTarget(course, leaderIn(targetDeanery)));
    }

    @Test
    void courseWithoutTargetsOrDeaneryCourseIsNeverOffTarget() {
        assertFalse(support.isOffTarget(course(UnitType.DIOCESE), leaderIn(UUID.randomUUID())));
        assertFalse(support.isOffTarget(course(UnitType.DEANERY, targetDeanery), leaderIn(UUID.randomUUID())));
    }

    @Test
    void offTargetAndCrossDeaneryParticipationsRequireManualApproval() {
        CourseParticipation plain = new CourseParticipation();
        assertFalse(CourseParticipationService.requiresManualApproval(plain));
        CourseParticipation offTarget = new CourseParticipation();
        offTarget.setOffTargetReason("lý do");
        assertTrue(CourseParticipationService.requiresManualApproval(offTarget));
        CourseParticipation cross = new CourseParticipation();
        cross.setCrossDeaneryReason("lý do");
        assertTrue(CourseParticipationService.requiresManualApproval(cross));
    }
}
