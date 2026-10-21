package phucuong.ht.modules.training.trainingCourse.model.response;

import lombok.Builder;
import lombok.Getter;

// Tác động của việc đổi trạng thái khóa: số chứng nhận bị thu hồi khi vô hiệu hóa / được mở lại khi bật lại.
@Getter
@Builder
public class CourseStatusImpactResponse {
    private boolean active;
    private long approvedCertificates;
    private long restorableCertificates;
}
