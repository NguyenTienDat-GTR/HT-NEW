package phucuong.ht.modules.training;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tư cách gửi đăng ký khóa huấn luyện của tài khoản. Tài khoản nhiều role có thể gửi với nhiều tư cách
 * (giáo phận, giáo hạt, giáo xứ): mỗi tư cách cần quyền gửi của cấp đó và đơn vị hiệu lực ở cấp đó.
 */
@Component
@RequiredArgsConstructor
public class RegistrationSenderResolver {

    /** Một tư cách gửi cho giao diện chọn: mã (DIOCESE | DEANERY | PARISH) và nhãn hiển thị. */
    public record Option(String code, String label) {
    }

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CommonValidationHelper commonValidationHelper;

    /** Các tư cách khả dụng, từ cấp cao tới cấp thấp (giáo phận, giáo hạt, giáo xứ). */
    public List<UnitType> available(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        List<UnitType> senders = new ArrayList<>();
        if (username == null || username.isBlank()) {
            return senders;
        }
        if (securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_DIOCESE)
                && accountRepository.findActiveDioceseIdByUsername(username).isPresent()) {
            senders.add(UnitType.DIOCESE);
        }
        if (securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY)
                && accountRepository.findActiveDeaneryIdByUsername(username).isPresent()) {
            senders.add(UnitType.DEANERY);
        }
        if (securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH)
                && accountRepository.findActiveParishIdByUsername(username).isPresent()) {
            senders.add(UnitType.PARISH);
        }
        return senders;
    }

    public List<Option> options(Authentication authentication) {
        return available(authentication).stream().map(sender -> new Option(sender.name(), labelOf(sender))).toList();
    }

    private static String labelOf(UnitType sender) {
        return switch (sender) {
            case DIOCESE -> "Giáo phận";
            case DEANERY -> "Giáo hạt";
            case PARISH -> "Giáo xứ";
            default -> sender.name();
        };
    }

    /** Tư cách được chọn (nếu có) phải nằm trong danh sách khả dụng; không chọn thì lấy cấp cao nhất. */
    public UnitType resolve(Authentication authentication, UnitType requested) {
        List<UnitType> available = available(authentication);
        if (available.isEmpty()) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gửi đăng ký khóa huấn luyện");
        }
        return pick(available, requested);
    }

    /** Như {@link #resolve}, nhưng khi không chọn thì lấy tư cách đầu tiên phù hợp với khóa. */
    public UnitType resolveForCourse(Authentication authentication, UnitType requested, TrainingCourse course) {
        List<UnitType> available = available(authentication);
        if (available.isEmpty()) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gửi đăng ký khóa huấn luyện");
        }
        if (requested == null) {
            return availableForCourse(authentication, course).stream().findFirst().orElse(available.get(0));
        }
        return pick(available, requested);
    }

    /** Các tư cách khả dụng mà khóa nằm trong phạm vi của tư cách đó. */
    public List<UnitType> availableForCourse(Authentication authentication, TrainingCourse course) {
        String username = securityUtils.currentUsername(authentication);
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        UUID courseDiocese = trainingCourseRepository.findDioceseIdByCourseCode(course.getCourseCode()).orElse(null);
        UUID courseDeanery = trainingCourseRepository.findDeaneryIdByCourseCode(course.getCourseCode()).orElse(null);
        UUID myDiocese = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        UUID myDeanery = accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
        List<UnitType> result = new ArrayList<>();
        for (UnitType sender : available(authentication)) {
            boolean fits = switch (sender) {
                // Giáo phận chỉ gửi cho khóa thuộc giáo phận mình.
                case DIOCESE -> !deaneryManaged && courseDiocese != null && courseDiocese.equals(myDiocese);
                // Giáo hạt và giáo xứ: khóa giáo hạt của giáo hạt mình, hoặc khóa giáo phận chủ quản.
                case DEANERY, PARISH -> deaneryManaged
                        ? courseDeanery != null && courseDeanery.equals(myDeanery)
                        : courseDiocese != null && courseDiocese.equals(myDiocese);
                default -> false;
            };
            if (fits) {
                result.add(sender);
            }
        }
        return result;
    }

    private UnitType pick(List<UnitType> available, UnitType requested) {
        if (requested == null) {
            return available.get(0);
        }
        if (!available.contains(requested)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có tư cách gửi đăng ký đã chọn");
        }
        return requested;
    }
}
