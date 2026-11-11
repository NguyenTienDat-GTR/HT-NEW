package phucuong.ht.modules.training.courseType.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.Optional;
import java.util.UUID;

/**
 * Loại khóa thuộc riêng từng giáo phận: tài khoản chỉ được xem / dùng / sửa loại khóa của giáo phận mình
 * (tài khoản giáo phận, giáo hạt, giáo xứ, huynh trưởng đều xác định được giáo phận).
 * Tài khoản không thuộc giáo phận nào (super admin) không bị giới hạn khi đọc nhưng không tạo được loại khóa.
 */
@Component
@RequiredArgsConstructor
public class CourseTypeDioceseGuard {

    private final LeaderLevelService leaderLevelService;
    private final CommonValidationHelper validation;
    private final CourseTypeOwnership ownership;

    public Optional<UUID> actorDioceseId(Authentication authentication) {
        return leaderLevelService.activeDioceseId(authentication);
    }

    public UUID requireActorDioceseId(Authentication authentication) {
        return actorDioceseId(authentication)
                .orElseThrow(() -> validation.forbidden("Tài khoản chưa thuộc giáo phận nào đang hoạt động"));
    }

    public void requireSameDiocese(TrainingCourseType courseType, Authentication authentication) {
        actorDioceseId(authentication).ifPresent(dioceseId -> requireInDiocese(courseType, dioceseId));
        // Loại khóa do giáo hạt khác tự tạo thì không xem / dùng được.
        if (courseType != null) {
            ownership.requireVisible(courseType, authentication);
        }
    }

    public void requireInDiocese(TrainingCourseType courseType, UUID dioceseId) {
        if (courseType != null && dioceseId != null && !dioceseId.equals(courseType.getDioceseId())) {
            throw validation.forbidden("Loại khóa huấn luyện không thuộc giáo phận của bạn");
        }
    }
}
