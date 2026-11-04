package phucuong.ht.modules.training.crossDeanery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryAccessDecision;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.AccessDecisionRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CourseInvitation;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryCourseAccess;
import phucuong.ht.modules.training.crossDeanery.repository.CrossDeaneryCourseAccessRepository;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Khóa giáo hạt A mở đăng ký cho các giáo hạt khác cùng giáo phận: admin giáo hạt B nhận thông báo kèm thông tin khóa rồi quyết định
 * cho phép / từ chối giáo xứ của mình gửi hồ sơ liên giáo hạt. Chỉ khi được cho phép giáo xứ mới gửi được (xem {@link CrossDeaneryService}).
 */
@Service
@RequiredArgsConstructor
public class CrossDeaneryAccessService {

    public static final String EVENT_COURSE_OPENED = "training.cross_deanery.course_opened";
    public static final String EVENT_ACCESS_ALLOWED = "training.cross_deanery.access_allowed";
    public static final String EVENT_ACCESS_DENIED = "training.cross_deanery.access_denied";
    public static final String DECISION_PENDING = "PENDING";
    private static final int MAX_NOTE = 500;

    private final CrossDeaneryCourseAccessRepository accessRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final CrossDeaneryCourseInfoBuilder courseInfoBuilder;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CourseActivityGuard courseActivityGuard;
    private final CommonValidationHelper commonValidationHelper;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final securityUtils securityUtils;
    private final AuditActorNameResolver auditActorNameResolver;
    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final TrainingNotificationService trainingNotificationService;

    // Khóa của các giáo hạt khác đang mở liên giáo hạt, kèm quyết định hiện tại của giáo hạt nhà.
    @Transactional(readOnly = true)
    public List<CourseInvitation> list(Authentication authentication) {
        Deanery home = requireHomeDeanery(authentication);
        UUID dioceseId = home.getDiocese() == null ? null : home.getDiocese().getId();
        if (dioceseId == null) {
            return List.of();
        }
        Map<String, CrossDeaneryCourseAccess> decided = accessRepository.findByDeanery(home.getId()).stream()
                .collect(Collectors.toMap(CrossDeaneryCourseAccess::getCourseCode, Function.identity(), (a, b) -> a));
        return trainingCourseRepository.findCoursesOpenToDeanery(dioceseId, home.getId()).stream()
                .map(course -> toInvitation(course, decided.get(course.getCourseCode())))
                .toList();
    }

    // Đổi quyết định cho phép / từ chối khóa liên giáo hạt: xóa cache khóa để danh sách / chi tiết không giữ dữ liệu cũ.
    @EvictCaches.Courses
    @Transactional
    public CourseInvitation decide(String courseCode, AccessDecisionRequest request, Authentication authentication) {
        Deanery home = requireHomeDeanery(authentication);
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        requireOpenFor(course, home);
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (note != null && note.length() > MAX_NOTE) {
            throw commonValidationHelper.validationError("Ghi chú tối đa " + MAX_NOTE + " ký tự");
        }
        if (!request.allowed() && note == null) {
            throw commonValidationHelper.validationError("Phải nhập lí do khi từ chối khóa này");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người xử lý");
        OffsetDateTime now = OffsetDateTime.now();
        CrossDeaneryCourseAccess access = accessRepository.findByCourseAndDeanery(courseCode, home.getId()).orElse(null);
        boolean created = access == null;
        if (created) {
            access = new CrossDeaneryCourseAccess();
            access.setCourseCode(courseCode);
            access.setDeaneryId(home.getId());
            access.setCreatedAt(now);
            access.setCreatedBy(actor);
            access.setStatus(Boolean.TRUE);
        }
        CrossDeaneryAccessDecision previous = access.getDecision();
        CrossDeaneryAccessDecision decision = request.allowed() ? CrossDeaneryAccessDecision.ALLOWED : CrossDeaneryAccessDecision.DENIED;
        access.setDecision(decision);
        access.setDecisionNote(note);
        access.setDecidedBy(actor);
        access.setDecidedAt(now);
        access.setUpdatedAt(now);
        access.setUpdatedBy(actor);
        if (created) {
            accessRepository.save(access);
        }
        if (previous != decision) {
            notifyDecision(course, home, decision, note, actor);
        }
        return toInvitation(course, access);
    }

    // Gọi khi khóa giáo hạt vừa mở liên giáo hạt (tạo mới hoặc bật khi sửa): báo cho admin các giáo hạt khác chưa có quyết định.
    public void notifyCourseOpened(TrainingCourse course, String actor) {
        if (course.getDeanery() == null || course.getDeanery().getDiocese() == null) {
            return;
        }
        UUID host = course.getDeanery().getId();
        Set<UUID> decided = accessRepository.findByCourse(course.getCourseCode()).stream()
                .map(CrossDeaneryCourseAccess::getDeaneryId).collect(Collectors.toSet());
        Set<String> recipients = new LinkedHashSet<>();
        for (UUID deaneryId : deaneryRepository.findActiveIdsByDioceseIdExcluding(course.getDeanery().getDiocese().getId(), host)) {
            if (!decided.contains(deaneryId)) {
                recipients.addAll(notificationRecipientResolver.resolveRecipients(null,
                        CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY, UnitType.DEANERY, deaneryId));
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        trainingNotificationService.notifyUsers(new ArrayList<>(recipients), TrainingNotificationCommand.builder()
                .eventType(EVENT_COURSE_OPENED)
                .title("Khóa mở đăng ký liên giáo hạt")
                .message(course.getDeanery().getName() + " mở khóa " + courseLabel(course)
                        + " cho các giáo hạt khác trong giáo phận. Chọn thông báo để xem thông tin khóa và quyết định có cho giáo xứ của giáo hạt gửi hồ sơ hay không.")
                .senderUnitType(UnitType.DEANERY)
                .senderUnitId(host)
                .courseCode(course.getCourseCode())
                .actor(actor)
                .build());
    }

    private void notifyDecision(TrainingCourse course, Deanery home, CrossDeaneryAccessDecision decision, String note, String actor) {
        boolean allowed = decision == CrossDeaneryAccessDecision.ALLOWED;
        // Giáo hạt tổ chức khóa: những người duyệt đăng ký của giáo hạt đó.
        List<String> hostRecipients = notificationRecipientResolver.resolveRecipients(null,
                CourseParticipationAccessEvaluator.APPROVE_DEANERY, UnitType.DEANERY, course.getDeanery().getId());
        trainingNotificationService.notifyUsers(hostRecipients, TrainingNotificationCommand.builder()
                .eventType(allowed ? EVENT_ACCESS_ALLOWED : EVENT_ACCESS_DENIED)
                .title(allowed ? "Giáo hạt đồng ý nhận đăng ký liên giáo hạt" : "Giáo hạt từ chối đăng ký liên giáo hạt")
                .message(home.getName() + (allowed ? " cho phép giáo xứ gửi hồ sơ sang khóa " : " từ chối cho giáo xứ gửi hồ sơ sang khóa ")
                        + courseLabel(course) + (note == null ? "." : ": " + note))
                .senderUnitType(UnitType.DEANERY)
                .senderUnitId(home.getId())
                .courseCode(course.getCourseCode())
                .actor(actor)
                .build());
        if (!allowed) {
            return;
        }
        // Giáo xứ của giáo hạt nhà: những tài khoản có quyền gửi hồ sơ, để biết đã gửi được.
        Set<String> parishRecipients = new LinkedHashSet<>();
        for (UUID parishId : parishRepository.findActiveIdsByDeaneryId(home.getId())) {
            parishRecipients.addAll(notificationRecipientResolver.resolveRecipients(null,
                    CourseParticipationAccessEvaluator.SUBMIT_PARISH, UnitType.PARISH, parishId));
        }
        trainingNotificationService.notifyUsers(new ArrayList<>(parishRecipients), TrainingNotificationCommand.builder()
                .eventType(EVENT_ACCESS_ALLOWED)
                .title("Giáo xứ có thể gửi hồ sơ sang khóa của giáo hạt khác")
                .message(home.getName() + " đã cho phép gửi hồ sơ sang khóa " + courseLabel(course) + " của " + course.getDeanery().getName()
                        + ". Vào Đăng ký tham dự > Liên giáo hạt để gửi.")
                .senderUnitType(UnitType.DEANERY)
                .senderUnitId(home.getId())
                .courseCode(course.getCourseCode())
                .actor(actor)
                .build());
    }

    // Khóa phải là khóa giáo hạt của giáo hạt KHÁC cùng giáo phận và đang mở liên giáo hạt.
    private void requireOpenFor(TrainingCourse course, Deanery home) {
        if (course.isPast() || !trainingWorkflowSupport.isDeaneryManaged(course) || course.getDeanery() == null
                || course.getDeanery().getId().equals(home.getId())) {
            throw commonValidationHelper.validationError("Chỉ quyết định với khóa của giáo hạt khác");
        }
        UUID courseDiocese = course.getDeanery().getDiocese() == null ? null : course.getDeanery().getDiocese().getId();
        UUID homeDiocese = home.getDiocese() == null ? null : home.getDiocese().getId();
        if (courseDiocese == null || !courseDiocese.equals(homeDiocese)) {
            throw commonValidationHelper.forbidden("Chỉ quyết định được với khóa của giáo hạt cùng giáo phận");
        }
        if (!Boolean.TRUE.equals(course.getOpenToOtherDeaneries())) {
            throw commonValidationHelper.conflict("Khóa này không mở đăng ký cho giáo hạt khác");
        }
    }

    private Deanery requireHomeDeanery(Authentication authentication) {
        if (!securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền quyết định đăng ký liên giáo hạt");
        }
        UUID id = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY);
        return deaneryRepository.findByIdWithDiocese(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt " + id));
    }

    private CourseInvitation toInvitation(TrainingCourse course, CrossDeaneryCourseAccess access) {
        String decision = access == null ? DECISION_PENDING : access.getDecision().name();
        boolean closed = course.getRegistrationStatus() == CourseRegistrationStatus.ENDED || course.getRegistrationStatus() == CourseRegistrationStatus.CLOSED;
        return new CourseInvitation(course.getCourseCode(), course.getCourseName(), course.getDeanery().getId(), course.getDeanery().getName(),
                courseInfoBuilder.build(course), decision, access == null ? null : access.getDecisionNote(),
                access == null ? null : auditActorNameResolver.resolve(access.getDecidedBy()), access == null ? null : access.getDecidedAt(),
                closed, !closed);
    }

    private static String courseLabel(TrainingCourse course) {
        return course.getCourseName() == null || course.getCourseName().isBlank() ? course.getCourseCode() : course.getCourseName();
    }
}
