package phucuong.ht.modules.training.courseParticipation.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

@Component("courseParticipationAccessEvaluator")
@RequiredArgsConstructor
public class CourseParticipationAccessEvaluator {

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    public static final String SUBMIT_PARISH = "training.participation.submit.parish";
    public static final String SUBMIT_DIOCESE = "training.participation.submit.diocese";
    public static final String SUBMIT_DEANERY = "training.participation.submit.deanery";
    public static final String APPROVE_DEANERY = "training.participation.approve.deanery";
    public static final String APPROVE_DIOCESE = "training.participation.approve.diocese";
    public static final String SCORE_DEANERY = "training.participation.score.deanery";
    public static final String SCORE_DIOCESE = "training.participation.score.diocese";
    public static final String READ_PARISH = "training.participation.read.parish";
    public static final String READ_DEANERY = "training.participation.read.deanery";
    public static final String READ_DIOCESE = "training.participation.read.diocese";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    // Gửi đăng ký: giáo xứ (khóa giáo phận/giáo hạt trong phạm vi), giáo phận (chỉ khóa giáo phận), giáo hạt (chỉ khóa giáo hạt).
    public boolean canRegister(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return trainingCourseRepository.findByCodeWithType(courseCode)
                .map(course -> {
                    boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course.getCourseType());
                    UnitType sender = trainingWorkflowSupport.resolveRegistrationSender(
                            securityUtils.hasAuthority(authentication, SUBMIT_PARISH),
                            securityUtils.hasAuthority(authentication, SUBMIT_DIOCESE),
                            securityUtils.hasAuthority(authentication, SUBMIT_DEANERY),
                            accountRepository.findDirectActiveDioceseIdByUsername(username).isPresent(),
                            accountRepository.findDirectActiveDeaneryIdByUsername(username).isPresent());
                    if (sender == UnitType.PARISH) {
                        return accountRepository.findActiveParishIdByUsername(username).isPresent()
                                && (deaneryManaged ? scopedDeanery(authentication, courseCode) : scopedDiocese(authentication, courseCode));
                    }
                    if (sender == UnitType.DIOCESE) {
                        return !deaneryManaged && directDioceseOwnsCourse(authentication, courseCode);
                    }
                    if (sender == UnitType.DEANERY) {
                        return deaneryManaged && directDeaneryOwnsCourse(authentication, courseCode);
                    }
                    return false;
                })
                .orElse(false);
    }

    // Thu hồi: tài khoản có quyền gửi đăng ký; service kiểm tra thêm đúng người đã gửi.
    public boolean canWithdraw(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, SUBMIT_PARISH)
                || securityUtils.hasAuthority(authentication, SUBMIT_DIOCESE)
                || securityUtils.hasAuthority(authentication, SUBMIT_DEANERY);
    }

    // Duyệt/từ chối hàng loạt: có quyền duyệt giáo hạt hoặc giáo phận; service kiểm tra từng hồ sơ.
    public boolean canBulkApprove(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, APPROVE_DEANERY)
                || securityUtils.hasAuthority(authentication, APPROVE_DIOCESE);
    }

    // Xóa hồ sơ khỏi danh sách tham dự: người có quyền duyệt; service kiểm tra đúng cấp quản lý của khóa.
    public boolean canRemoveParticipant(Authentication authentication) {
        return canBulkApprove(authentication);
    }

    public boolean canDeaneryApprove(Authentication authentication, String participationId) {
        return securityUtils.hasAuthority(authentication, APPROVE_DEANERY)
                && courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())
                        && deaneryScopeOwnsCourse(authentication, participation.getCourse().getCourseCode()))
                .orElse(false);
    }

    public boolean canDioceseApprove(Authentication authentication, String participationId) {
        return securityUtils.hasAuthority(authentication, APPROVE_DIOCESE)
                && courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> !trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())
                        && dioceseScopeOwnsCourse(authentication, participation.getCourse().getCourseCode()))
                .orElse(false);
    }

    public boolean canScore(Authentication authentication, String participationId) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        return courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> {
                    if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
                        return securityUtils.hasAuthority(authentication, SCORE_DEANERY)
                                && scopedDeanery(authentication, participation.getCourse().getCourseCode());
                    }
                    return securityUtils.hasAuthority(authentication, SCORE_DIOCESE)
                            && scopedDiocese(authentication, participation.getCourse().getCourseCode());
                })
                .orElse(false);
    }

    // Xem thành phần điểm của khóa để nhập điểm: người có quyền chấm điểm đúng cấp quản lý của khóa.
    public boolean canScoreCourse(Authentication authentication, String courseCode) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return false;
        }
        return trainingCourseRepository.findByCodeWithType(courseCode)
                .map(course -> trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())
                        ? securityUtils.hasAuthority(authentication, SCORE_DEANERY) && scopedDeanery(authentication, courseCode)
                        : securityUtils.hasAuthority(authentication, SCORE_DIOCESE) && scopedDiocese(authentication, courseCode))
                .orElse(false);
    }

    public boolean canRead(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_PARISH)
                || securityUtils.hasAuthority(authentication, READ_DEANERY)
                || securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, APPROVE_DEANERY)
                || securityUtils.hasAuthority(authentication, APPROVE_DIOCESE)
                || securityUtils.hasAuthority(authentication, SCORE_DEANERY)
                || securityUtils.hasAuthority(authentication, SCORE_DIOCESE);
    }

    public boolean canReadOne(Authentication authentication, String participationId) {
        return canRead(authentication) && courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> {
                    if (securityUtils.hasAuthority(authentication, READ_PARISH) && scopedParish(authentication, participation)) {
                        return true;
                    }
                    if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
                        return hasDeaneryReadAuthority(authentication)
                                && scopedDeanery(authentication, participation.getCourse().getCourseCode());
                    }
                    return hasDioceseReadAuthority(authentication)
                            && scopedDiocese(authentication, participation.getCourse().getCourseCode());
                })
                .orElse(false);
    }

    private boolean hasDeaneryReadAuthority(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_DEANERY)
                || securityUtils.hasAuthority(authentication, APPROVE_DEANERY)
                || securityUtils.hasAuthority(authentication, SCORE_DEANERY);
    }

    private boolean hasDioceseReadAuthority(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, APPROVE_DIOCESE)
                || securityUtils.hasAuthority(authentication, SCORE_DIOCESE);
    }

    private boolean scopedParish(Authentication authentication, CourseParticipation participation) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()
                || participation.getLeader() == null
                || participation.getLeader().getParish() == null) {
            return false;
        }
        return accountRepository.findActiveParishIdByUsername(username)
                .map(activeId -> activeId.equals(participation.getLeader().getParish().getId()))
                .orElse(false);
    }

    private boolean scopedDeanery(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findActiveDeaneryIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDeaneryIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    private boolean scopedDiocese(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDioceseIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    private boolean directDeaneryInCourseDiocese(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()
                || accountRepository.findDirectActiveDeaneryIdByUsername(username).isEmpty()) {
            return false;
        }
        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDioceseIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    // Duyệt: phạm vi của tài khoản (gắn trực tiếp hoặc theo huynh trưởng) phải chứa khóa; cấp duyệt do quyền quyết định.
    private boolean deaneryScopeOwnsCourse(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findActiveDeaneryIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDeaneryIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    private boolean dioceseScopeOwnsCourse(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDioceseIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    private boolean directDeaneryOwnsCourse(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findDirectActiveDeaneryIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDeaneryIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    private boolean directDioceseOwnsCourse(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findDirectActiveDioceseIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDioceseIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }
}
