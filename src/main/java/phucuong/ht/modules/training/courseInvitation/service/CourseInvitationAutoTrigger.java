package phucuong.ht.modules.training.courseInvitation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

// Điểm nối từ luồng duyệt đăng ký: khóa bật "tự gửi email thông tin tham dự" thì gửi cho hồ sơ vừa được duyệt,
// sau khi giao dịch duyệt đã commit (để luồng nền đọc đúng dữ liệu và hồ sơ bị rollback không bị gửi nhầm).
@Component
@RequiredArgsConstructor
public class CourseInvitationAutoTrigger {

    private final CourseInvitationAsyncRunner runner;

    public void afterApproved(Collection<CourseParticipation> approved) {
        if (approved == null || approved.isEmpty()) {
            return;
        }
        Map<String, List<String>> idsByCourse = new LinkedHashMap<>();
        for (CourseParticipation participation : approved) {
            TrainingCourse course = participation.getCourse();
            if (course == null || course.isPast() || !Boolean.TRUE.equals(course.getAutoSendInvitation()) || participation.getId() == null) {
                continue;
            }
            idsByCourse.computeIfAbsent(course.getCourseCode(), key -> new ArrayList<>()).add(participation.getId());
        }
        if (idsByCourse.isEmpty()) {
            return;
        }
        Runnable dispatch = () -> idsByCourse.forEach(runner::autoSend);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
