package phucuong.ht.modules.training.trainingCourse.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

@Component("trainingCourseAccessEvaluator")
@RequiredArgsConstructor
public class TrainingCourseAccessEvaluator {

    public static final String CREATE_DIOCESE = "training.course.create.diocese";
    public static final String READ_DIOCESE = "training.course.read.diocese";
    public static final String UPDATE_DIOCESE = "training.course.update.diocese";
    public static final String TOGGLE_DIOCESE = "training.course.toggle.diocese";
    public static final String CREATE_DEANERY = "training.course.create.deanery";
    public static final String READ_DEANERY = "training.course.read.deanery";
    public static final String UPDATE_DEANERY = "training.course.update.deanery";
    public static final String TOGGLE_DEANERY = "training.course.toggle.deanery";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    public boolean canCreate(Authentication authentication, String courseTypeCode) {
        if (courseTypeCode == null || courseTypeCode.isBlank()) {
            // Để service trả lỗi validation rõ ràng thay vì 403.
            return securityUtils.hasAuthority(authentication, CREATE_DIOCESE)
                    || securityUtils.hasAuthority(authentication, CREATE_DEANERY);
        }
        return trainingCourseTypeRepository.findByCodeWithPrerequisite(courseTypeCode.trim())
                .map(courseType -> trainingWorkflowSupport.isDeaneryManaged(courseType)
                        ? securityUtils.hasAuthority(authentication, CREATE_DEANERY)
                        : securityUtils.hasAuthority(authentication, CREATE_DIOCESE))
                // Loại khóa không tồn tại: cho qua để service trả 404 nếu người dùng có quyền tạo.
                .orElseGet(() -> securityUtils.hasAuthority(authentication, CREATE_DIOCESE)
                        || securityUtils.hasAuthority(authentication, CREATE_DEANERY));
    }

    public static final String SUBMIT_PARISH = "training.participation.submit.parish";
    public static final String LOCK_SCORE_DIOCESE = "training.participation.lock.diocese";
    public static final String LOCK_SCORE_DEANERY = "training.participation.lock.deanery";
    public static final String FINISH_DIOCESE = "training.course.finish.diocese";
    public static final String FINISH_DEANERY = "training.course.finish.deanery";

    // Tài khoản giáo xứ cũng xem được danh sách khóa, nhưng chỉ các khóa đang mở đăng ký trong phạm vi.
    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, READ_DEANERY)
                || securityUtils.hasAuthority(authentication, SUBMIT_PARISH);
    }

    public boolean canListRegistrable(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, "training.participation.submit.parish")
                || securityUtils.hasAuthority(authentication, "training.participation.submit.diocese")
                || securityUtils.hasAuthority(authentication, "training.participation.submit.deanery");
    }

    public boolean canRead(Authentication authentication, String courseCode) {
        return hasScopedAccess(authentication, courseCode, READ_DIOCESE, READ_DEANERY)
                || canReadOpenCourseAsParish(authentication, courseCode);
    }

    // Giáo xứ chỉ xem khóa đang mở đăng ký thuộc giáo phận (khóa thường) hoặc giáo hạt (khóa giáo hạt) của mình.
    private boolean canReadOpenCourseAsParish(Authentication authentication, String courseCode) {
        if (courseCode == null || courseCode.isBlank() || !securityUtils.hasAuthority(authentication, SUBMIT_PARISH)) {
            return false;
        }
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank() || accountRepository.findActiveParishIdByUsername(username).isEmpty()) {
            return false;
        }
        return trainingCourseRepository.findByCodeWithType(courseCode)
                .map(course -> {
                    if (course.getRegistrationStatus() != phucuong.ht.modules.training.enums.CourseRegistrationStatus.OPEN
                            || !Boolean.TRUE.equals(course.getStatus())) {
                        return false;
                    }
                    if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
                        return course.getDeanery() != null && accountRepository.findActiveDeaneryIdByUsername(username)
                                .map(course.getDeanery().getId()::equals).orElse(false);
                    }
                    return course.getDiocese() != null && accountRepository.findActiveDioceseIdByUsername(username)
                            .map(course.getDiocese().getId()::equals).orElse(false);
                })
                .orElse(false);
    }

    // Khóa/mở chỉnh sửa điểm: admin giáo phận (khóa giáo phận) hoặc giáo hạt (khóa giáo hạt) sở hữu khóa.
    public boolean canLockScore(Authentication authentication, String courseCode) {
        return hasScopedAccess(authentication, courseCode, LOCK_SCORE_DIOCESE, LOCK_SCORE_DEANERY);
    }

    // Kết thúc khóa thủ công: admin giáo phận (khóa giáo phận) hoặc giáo hạt (khóa giáo hạt) sở hữu khóa.
    public boolean canFinish(Authentication authentication, String courseCode) {
        return hasScopedAccess(authentication, courseCode, FINISH_DIOCESE, FINISH_DEANERY);
    }

    public boolean canUpdate(Authentication authentication, String courseCode) {
        return hasScopedAccess(authentication, courseCode, UPDATE_DIOCESE, UPDATE_DEANERY);
    }

    public boolean canToggle(Authentication authentication, String courseCode) {
        return hasScopedAccess(authentication, courseCode, TOGGLE_DIOCESE, TOGGLE_DEANERY);
    }

    private boolean hasScopedAccess(Authentication authentication, String courseCode, String dioceseAuthority, String deaneryAuthority) {
        if (courseCode == null || courseCode.isBlank()) {
            return false;
        }

        return trainingCourseRepository.findByCodeWithType(courseCode)
                .map(course -> {
                    if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
                        if (!securityUtils.hasAuthority(authentication, deaneryAuthority)) {
                            return false;
                        }
                        String username = securityUtils.currentUsername(authentication);
                        if (username == null || username.isBlank()) {
                            return false;
                        }
                        return accountRepository.findActiveDeaneryIdByUsername(username)
                                .flatMap(activeId -> trainingCourseRepository.findDeaneryIdByCourseCode(courseCode).map(activeId::equals))
                                .orElse(false);
                    }

                    if (!securityUtils.hasAuthority(authentication, dioceseAuthority)) {
                        return false;
                    }
                    String username = securityUtils.currentUsername(authentication);
                    if (username == null || username.isBlank()) {
                        return false;
                    }
                    return accountRepository.findActiveDioceseIdByUsername(username)
                            .flatMap(activeId -> trainingCourseRepository.findDioceseIdByCourseCode(courseCode).map(activeId::equals))
                            .orElse(false);
                })
                .orElse(false);
    }
}
