package phucuong.ht.modules.training;

import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.util.UUID;

// Dữ liệu dựng sẵn (không cần DB) cho test thẻ tham dự, điểm danh và email thông tin tham dự.
public final class TrainingTestFixtures {

    private TrainingTestFixtures() {
    }

    public static Diocese diocese(String name) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName(name);
        return diocese;
    }

    public static Deanery deanery(String name, Diocese diocese) {
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setName(name);
        deanery.setDiocese(diocese);
        return deanery;
    }

    public static Parish parish(String name, Deanery deanery) {
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setName(name);
        parish.setDeanery(deanery);
        return parish;
    }

    public static Leader leader(String holyName, String lastName, String firstName, String email, Parish parish) {
        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setHolyName(holyName);
        leader.setLastName(lastName);
        leader.setFirstName(firstName);
        leader.setEmail(email);
        leader.setParish(parish);
        return leader;
    }

    // Khóa thường do giáo hạt quản lý, bắt đầu 5 ngày nữa nên chưa kết thúc.
    public static TrainingCourse course(String code, String name, Deanery deanery) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("DU-TRUONG");
        type.setTypeName("Dự trưởng");
        type.setManagementUnitType(deanery == null ? UnitType.DIOCESE : UnitType.DEANERY);

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseName(name);
        course.setHostYear(2026);
        course.setLocation("Nhà thờ Chánh Tòa");
        course.setStartDate(LocalDate.now().plusDays(5));
        course.setEndDate(LocalDate.now().plusDays(9));
        course.setCourseType(type);
        course.setDeanery(deanery);
        course.setDiocese(deanery == null ? null : deanery.getDiocese());
        course.setStatus(Boolean.TRUE);
        return course;
    }

    // Hồ sơ đã được duyệt vào danh sách tham dự.
    public static CourseParticipation participation(String id, Leader leader, TrainingCourse course, String teamName) {
        CourseParticipation participation = new CourseParticipation();
        participation.setId(id);
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setTeamName(teamName);
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setParticipationStatus(ParticipationStatus.ATTENDED);
        participation.setStatus(Boolean.TRUE);
        return participation;
    }
}
