package phucuong.ht.modules.training;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Entry;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Chuyển hồ sơ đăng ký / chứng nhận thành dữ liệu thông báo kết quả (người gửi đăng ký + admin giáo xứ của hồ sơ).
@Component
@RequiredArgsConstructor
public class TrainingDecisionNotifier {

    private final WorkflowDecisionNotifier workflowDecisionNotifier;
    private final CourseParticipationRepository courseParticipationRepository;

    public void notifyRegistrations(Decision decision, List<CourseParticipation> participations, Map<String, String> notesById, String actor) {
        if (participations == null || participations.isEmpty()) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (CourseParticipation participation : participations) {
            String note = notesById == null ? null : notesById.get(participation.getId());
            entries.add(entryOf(participation, null, note));
        }
        workflowDecisionNotifier.notifyDecisions(decision, entries, actor);
    }

    // Hồ sơ sao chép từ khóa tiên quyết: người nhận là người gửi đăng ký hồ sơ gốc (submitterById theo mã hồ sơ mới) thay cho người sao chép.
    public void notifyRegistrations(Decision decision, List<CourseParticipation> participations, Map<String, String> notesById, String actor,
                                    Map<String, String> submitterById) {
        if (participations == null || participations.isEmpty()) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (CourseParticipation participation : participations) {
            String note = notesById == null ? null : notesById.get(participation.getId());
            Entry entry = entryOf(participation, null, note);
            String submitter = submitterById == null ? null : submitterById.get(participation.getId());
            entries.add(new Entry(submitter, entry.parishId(), entry.parishName(), entry.courseCode(), entry.courseName(),
                    entry.leaderName(), entry.documentCode(), entry.note(), entry.leaderId()));
        }
        workflowDecisionNotifier.notifyDecisions(decision, entries, actor);
    }

    public void notifyCertificates(Decision decision, List<Certificate> certificates, Map<String, String> notesByCode, String actor) {
        if (certificates == null || certificates.isEmpty()) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (Certificate certificate : certificates) {
            if (certificate.getLeader() == null || certificate.getCourse() == null) {
                continue;
            }
            Optional<CourseParticipation> participation = courseParticipationRepository
                    .findByLeaderIdAndCourseCode(certificate.getLeader().getId(), certificate.getCourse().getCourseCode());
            String note = notesByCode == null ? null : notesByCode.get(certificate.getCertificateCode());
            entries.add(participation
                    .map(item -> entryOf(item, certificate.getCertificateCode(), note))
                    .orElseGet(() -> entryOf(certificate, note)));
        }
        workflowDecisionNotifier.notifyDecisions(decision, entries, actor);
    }

    private Entry entryOf(CourseParticipation participation, String certificateCode, String note) {
        Leader leader = participation.getLeader();
        TrainingCourse course = participation.getCourse();
        return new Entry(
                participation.getCreatedBy(),
                leader == null || leader.getParish() == null ? null : leader.getParish().getId(),
                leader == null || leader.getParish() == null ? null : leader.getParish().getName(),
                course == null ? null : course.getCourseCode(),
                course == null ? null : course.getCourseName(),
                leader == null ? "" : AuditActorNameResolver.displayName(leader),
                certificateCode,
                note,
                leader == null ? null : leader.getId()
        );
    }

    private Entry entryOf(Certificate certificate, String note) {
        Leader leader = certificate.getLeader();
        TrainingCourse course = certificate.getCourse();
        return new Entry(
                null,
                leader.getParish() == null ? null : leader.getParish().getId(),
                leader.getParish() == null ? null : leader.getParish().getName(),
                course.getCourseCode(),
                course.getCourseName(),
                AuditActorNameResolver.displayName(leader),
                certificate.getCertificateCode(),
                note,
                leader.getId()
        );
    }
}
