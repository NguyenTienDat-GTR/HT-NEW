package phucuong.ht.modules.training.crossDeanery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryItemStatus;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.Candidate;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CreateRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.DecisionRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.ItemInput;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.ItemResponse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.OpenCourse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.RequestResponse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryStatus;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryRequest;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryRequestItem;
import phucuong.ht.modules.training.crossDeanery.repository.CrossDeaneryCourseAccessRepository;
import phucuong.ht.modules.training.crossDeanery.repository.CrossDeaneryRequestRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Gửi hồ sơ liên giáo hạt: giáo xứ ở giáo hạt B gửi huynh trưởng của mình sang khóa giáo hạt (vd Dự trưởng) của giáo hạt A cùng giáo phận.
 * Yêu cầu phải được người có quyền của giáo hạt B (giáo hạt nhà) đồng ý thì mới thành hồ sơ đăng ký chờ duyệt ở giáo hạt A;
 * giáo hạt B gửi thẳng thì coi như đã đồng ý. Mỗi hồ sơ kèm lí do riêng và luôn được duyệt thủ công (không tự duyệt).
 * Không thêm trạng thái mới vào hồ sơ đăng ký: các bước sau (duyệt, chấm điểm, chứng nhận...) giữ nguyên.
 */
@Service
@RequiredArgsConstructor
public class CrossDeaneryService {

    public static final String EVENT_REQUESTED = "training.cross_deanery.requested";
    public static final String EVENT_APPROVED = "training.cross_deanery.approved";
    public static final String EVENT_REJECTED = "training.cross_deanery.rejected";
    public static final String EVENT_CANCELLED = "training.cross_deanery.cancelled";
    private static final int MAX_REASON = 500;
    private static final int MAX_NOTE = 1000;

    private final CrossDeaneryRequestRepository requestRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final CourseParticipationService courseParticipationService;
    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CourseActivityGuard courseActivityGuard;
    private final CommonValidationHelper commonValidationHelper;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final phucuong.ht.modules.training.RegistrationSenderResolver registrationSenderResolver;
    private final securityUtils securityUtils;
    private final LeaderLevelService leaderLevelService;
    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final TrainingNotificationService trainingNotificationService;
    private final AuditActorNameResolver auditActorNameResolver;
    private final phucuong.ht.modules.auth.repository.AccountRepository accountRepository;
    private final CrossDeaneryCourseAccessRepository accessRepository;
    private final CrossDeaneryCourseInfoBuilder courseInfoBuilder;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;

    // Người gửi: giáo xứ (home = giáo hạt của giáo xứ) hoặc giáo hạt nhà gửi thẳng.
    private record Sender(UnitType type, UUID unitId, UUID parishId, String unitName, Deanery home) {
    }

    @Transactional(readOnly = true)
    public List<OpenCourse> openCourses(Authentication authentication) {
        Sender sender = resolveSender(authentication);
        UUID dioceseId = sender.home().getDiocese() == null ? null : sender.home().getDiocese().getId();
        if (dioceseId == null) {
            return List.of();
        }
        return trainingCourseRepository.findOpenDeaneryCoursesOfOtherDeaneries(dioceseId, sender.home().getId()).stream()
                .map(course -> new OpenCourse(course.getCourseCode(), course.getCourseName(),
                        course.getCourseType() == null ? null : course.getCourseType().getTypeName(),
                        course.getDeanery().getId(), course.getDeanery().getName(), course.getStartDate(), course.getEndDate(),
                        course.getRegistrationEndAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(String courseCode, Authentication authentication) {
        Sender sender = resolveSender(authentication);
        TrainingCourse course = requireCrossCourse(courseCode, sender);
        var levelOrder = leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));
        return leaderRepository.findActiveInScope(null, sender.home().getId(), sender.parishId()).stream().map(leader -> {
            boolean eligible = courseParticipationService.isEligible(leader.getLeaderLevel(), leader.getBirthDate(), leader.getId(), course);
            String blocked = null;
            if (courseParticipationRepository.existsByLeaderIdAndCourseCode(leader.getId(), courseCode)) {
                blocked = "Đã có hồ sơ trong khóa";
            } else if (requestRepository.existsPendingItem(leader.getId(), courseCode)) {
                blocked = "Đang chờ giáo hạt nhà đồng ý";
            } else if (!eligible && courseParticipationService.exceedsNearestAchievedLevel(leader.getLeaderLevel(), course, levelOrder)) {
                blocked = "Cấp độ cao hơn cấp độ đạt được gần nhất của loại khóa nên không gửi ngoại lệ được";
            }
            Parish parish = leader.getParish();
            return new Candidate(leader.getId(), leader.getHolyName(), fullName(leader), leader.getLeaderLevel(), parish.getId(), parish.getName(),
                    parish.getDeanery().getId(), parish.getDeanery().getName(), eligible, blocked);
        }).toList();
    }

    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public RequestResponse create(String courseCode, CreateRequest request, Authentication authentication) {
        Sender sender = resolveSender(authentication);
        TrainingCourse course = requireCrossCourse(courseCode, sender);
        courseParticipationService.requireActiveRegistrationData(course);
        courseParticipationService.requireRegistrationWindowOpen(course);
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gửi");
        OffsetDateTime now = OffsetDateTime.now();
        var levelOrder = leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));

        CrossDeaneryRequest entity = new CrossDeaneryRequest();
        entity.setCourse(course);
        entity.setHomeDeaneryId(sender.home().getId());
        entity.setParishId(sender.parishId());
        entity.setRequesterType(sender.type());
        entity.setRequestStatus(CrossDeaneryStatus.PENDING);
        entity.setCreatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        entity.setStatus(Boolean.TRUE);

        Set<UUID> seen = new HashSet<>();
        for (ItemInput input : request.items()) {
            if (!seen.add(input.leaderId())) {
                throw commonValidationHelper.validationError("Danh sách có huynh trưởng bị trùng");
            }
            Leader leader = leaderRepository.findByIdWithParish(input.leaderId())
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy huynh trưởng " + input.leaderId()));
            String name = fullName(leader);
            if (!Boolean.TRUE.equals(leader.getStatus())) {
                throw commonValidationHelper.validationError("Chỉ được gửi huynh trưởng đang hoạt động: " + name);
            }
            Parish parish = leader.getParish();
            if (parish == null || parish.getDeanery() == null || !sender.home().getId().equals(parish.getDeanery().getId())
                    || (sender.parishId() != null && !sender.parishId().equals(parish.getId()))) {
                throw commonValidationHelper.forbidden("Không thể gửi huynh trưởng ngoài phạm vi đơn vị hiện tại: " + name);
            }
            if (unitOperationalStatusService != null) {
                unitOperationalStatusService.requireUnitOperational(UnitType.PARISH, parish.getId());
            }
            if (courseParticipationRepository.existsByLeaderIdAndCourseCode(leader.getId(), courseCode)) {
                throw commonValidationHelper.conflict("Huynh trưởng " + name + " đã có hồ sơ trong khóa này");
            }
            if (requestRepository.existsPendingItem(leader.getId(), courseCode)) {
                throw commonValidationHelper.conflict("Huynh trưởng " + name + " đang có yêu cầu chờ giáo hạt nhà đồng ý");
            }
            String reason = cleanText(input.reason(), MAX_REASON, "Lí do gửi");
            if (reason == null) {
                throw commonValidationHelper.validationError("Phải nhập lí do gửi sang giáo hạt khác cho huynh trưởng " + name);
            }
            boolean eligible = courseParticipationService.isEligible(leader.getLeaderLevel(), leader.getBirthDate(), leader.getId(), course);
            String exceptionReason = null;
            if (!eligible) {
                exceptionReason = cleanText(input.exceptionReason(), MAX_REASON, "Lí do ngoại lệ");
                if (exceptionReason == null) {
                    throw commonValidationHelper.validationError("Huynh trưởng " + name + " chưa đủ điều kiện của khóa, hãy nhập lí do ngoại lệ");
                }
                if (courseParticipationService.exceedsNearestAchievedLevel(leader.getLeaderLevel(), course, levelOrder)) {
                    throw commonValidationHelper.validationError("Huynh trưởng " + name
                            + " có cấp độ cao hơn cấp độ đạt được gần nhất của loại khóa nên không thể gửi dạng ngoại lệ");
                }
            }
            CrossDeaneryRequestItem item = new CrossDeaneryRequestItem();
            item.setRequest(entity);
            item.setLeader(leader);
            item.setReason(reason);
            item.setExceptionReason(exceptionReason);
            item.setNote(cleanText(input.note(), MAX_NOTE, "Ghi chú"));
            item.setItemStatus(CrossDeaneryItemStatus.PENDING);
            item.setCreatedAt(now);
            item.setCreatedBy(actor);
            item.setUpdatedAt(now);
            item.setUpdatedBy(actor);
            item.setStatus(Boolean.TRUE);
            entity.getItems().add(item);
        }
        requestRepository.save(entity);

        if (sender.type() == UnitType.DEANERY) {
            // Giáo hạt nhà gửi thẳng: coi như đã đồng ý, hồ sơ vào danh sách chờ duyệt của giáo hạt tổ chức khóa.
            materialize(entity, entity.getItems(), actor, actor, now);
            entity.setRequestStatus(CrossDeaneryStatus.APPROVED);
            entity.setDecidedBy(actor);
            entity.setDecidedAt(now);
        } else {
            List<String> recipients = notificationRecipientResolver.resolveRecipients(null,
                    CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY, UnitType.DEANERY, sender.home().getId());
            trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                    .eventType(EVENT_REQUESTED)
                    .title("Yêu cầu gửi hồ sơ sang giáo hạt khác")
                    .message(sender.unitName() + " xin gửi " + entity.getItems().size() + " hồ sơ sang khóa " + courseLabel(course)
                            + " của " + course.getDeanery().getName() + ". Chọn thông báo để xem và đồng ý.")
                    .senderUnitType(UnitType.PARISH)
                    .senderUnitId(sender.unitId())
                    .courseCode(courseCode)
                    .totalCount(entity.getItems().size())
                    .approvedCount(0)
                    .pendingCount(entity.getItems().size())
                    .actor(actor)
                    .build());
        }
        return toResponse(entity, authentication, new Names());
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> list(CrossDeaneryStatus status, Authentication authentication) {
        List<CrossDeaneryRequest> found;
        if (securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY)) {
            found = requestRepository.findByHomeDeanery(
                    accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY), status);
        } else {
            Sender sender = resolveSender(authentication);
            found = sender.parishId() != null
                    ? requestRepository.findByParish(sender.parishId(), status)
                    : requestRepository.findByHomeDeanery(sender.home().getId(), status);
        }
        Names names = new Names();
        return found.stream()
                .filter(item -> status == null || effectiveStatus(item) == status)
                .map(item -> toResponse(item, authentication, names))
                .toList();
    }

    @Transactional(readOnly = true)
    public RequestResponse get(String id, Authentication authentication) {
        CrossDeaneryRequest request = requireRequest(id);
        requireVisible(request, authentication);
        return toResponse(request, authentication, new Names());
    }

    // Giáo hạt nhà đồng ý (cả yêu cầu hoặc một phần hồ sơ) hoặc từ chối; hồ sơ được đồng ý trở thành hồ sơ đăng ký chờ duyệt ở giáo hạt tổ chức khóa.
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public RequestResponse decide(String id, DecisionRequest decision, Authentication authentication) {
        CrossDeaneryRequest request = requireRequest(id);
        requireApprover(request, authentication);
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(request.getCourse().getCourseCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện"));
        if (request.getRequestStatus() != CrossDeaneryStatus.PENDING) {
            throw commonValidationHelper.conflict("Yêu cầu đã được xử lý hoặc đã bị thu hồi");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người xử lý");
        OffsetDateTime now = OffsetDateTime.now();
        String note = cleanText(decision.note(), MAX_REASON, "Ghi chú");
        List<CrossDeaneryRequestItem> pending = request.getItems().stream().filter(item -> item.getItemStatus() == CrossDeaneryItemStatus.PENDING).toList();

        List<CrossDeaneryRequestItem> approvedItems = new ArrayList<>();
        if (decision.approved()) {
            courseActivityGuard.requireActive(course);
            courseActivityGuard.requireNotEnded(course);
            courseParticipationService.requireRegistrationWindowOpen(course);
            if (entityLockService != null) {
                entityLockService.lock(course);
            }
            Set<String> chosen = decision.itemIds() == null ? Set.of() : new HashSet<>(decision.itemIds());
            Set<String> pendingIds = new HashSet<>();
            pending.forEach(item -> pendingIds.add(item.getId()));
            if (!pendingIds.containsAll(chosen)) {
                throw commonValidationHelper.validationError("Có hồ sơ không thuộc yêu cầu hoặc đã được xử lý");
            }
            for (CrossDeaneryRequestItem item : pending) {
                if (chosen.isEmpty() || chosen.contains(item.getId())) {
                    approvedItems.add(item);
                }
            }
            boolean someRejected = approvedItems.size() < pending.size();
            if (someRejected && note == null) {
                throw commonValidationHelper.validationError("Phải nhập lí do khi từ chối một phần hồ sơ");
            }
        } else if (note == null) {
            throw commonValidationHelper.validationError("Phải nhập lí do khi từ chối yêu cầu");
        }
        for (CrossDeaneryRequestItem item : pending) {
            if (!approvedItems.contains(item)) {
                item.setItemStatus(CrossDeaneryItemStatus.REJECTED);
                item.setUpdatedAt(now);
                item.setUpdatedBy(actor);
            }
        }
        if (!approvedItems.isEmpty()) {
            materialize(request, approvedItems, request.getCreatedBy(), actor, now);
        }
        boolean anyApproved = request.getItems().stream().anyMatch(item -> item.getItemStatus() == CrossDeaneryItemStatus.APPROVED);
        request.setRequestStatus(anyApproved ? CrossDeaneryStatus.APPROVED : CrossDeaneryStatus.REJECTED);
        request.setDecisionNote(note);
        request.setDecidedBy(actor);
        request.setDecidedAt(now);
        request.setUpdatedAt(now);
        request.setUpdatedBy(actor);
        notifyRequesterOfDecision(request, course, actor, approvedItems.size(), pending.size());
        return toResponse(request, authentication, new Names());
    }

    // Giáo xứ thu hồi yêu cầu khi giáo hạt nhà chưa xử lý.
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public RequestResponse cancel(String id, Authentication authentication) {
        CrossDeaneryRequest request = requireRequest(id);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi");
        if (!canCancel(request, authentication, actor)) {
            throw commonValidationHelper.forbidden("Không có quyền thu hồi yêu cầu này");
        }
        if (request.getRequestStatus() != CrossDeaneryStatus.PENDING) {
            throw commonValidationHelper.conflict("Yêu cầu đã được xử lý hoặc đã bị thu hồi");
        }
        OffsetDateTime now = OffsetDateTime.now();
        request.getItems().stream().filter(item -> item.getItemStatus() == CrossDeaneryItemStatus.PENDING).forEach(item -> {
            item.setItemStatus(CrossDeaneryItemStatus.CANCELLED);
            item.setUpdatedAt(now);
            item.setUpdatedBy(actor);
        });
        request.setRequestStatus(CrossDeaneryStatus.CANCELLED);
        request.setDecidedBy(actor);
        request.setDecidedAt(now);
        request.setUpdatedAt(now);
        request.setUpdatedBy(actor);
        notifyHomeDeaneryOfCancel(request, actor);
        return toResponse(request, authentication, new Names());
    }

    // Tạo hồ sơ đăng ký chờ duyệt ở khóa đích cho các hồ sơ được đồng ý; người gửi gốc là createdBy để thông báo kết quả về đúng giáo xứ.
    private void materialize(CrossDeaneryRequest request, List<CrossDeaneryRequestItem> items, String submitter, String approver, OffsetDateTime now) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(request.getCourse().getCourseCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện"));
        List<CourseParticipation> created = new ArrayList<>();
        for (CrossDeaneryRequestItem item : items) {
            Leader leader = item.getLeader();
            if (courseParticipationRepository.existsByLeaderIdAndCourseCode(leader.getId(), course.getCourseCode())) {
                // Trong lúc chờ, huynh trưởng đã được đưa vào khóa bằng đường khác: không tạo trùng.
                item.setItemStatus(CrossDeaneryItemStatus.REJECTED);
                continue;
            }
            boolean eligible = courseParticipationService.isEligible(leader.getLeaderLevel(), leader.getBirthDate(), leader.getId(), course);
            String exceptionReason = eligible ? null : (item.getExceptionReason() != null ? item.getExceptionReason() : item.getReason());
            CourseParticipation participation = courseParticipationService.newPendingParticipation(leader, course, eligible, exceptionReason, submitter, now);
            participation.setCrossDeaneryReason(item.getReason());
            participation.setHomeDeaneryId(request.getHomeDeaneryId());
            participation.setHomeDeaneryApprovedBy(approver);
            participation.setHomeDeaneryApprovedAt(now);
            participation.setRegistrationNote(item.getNote());
            courseParticipationRepository.save(participation);
            item.setParticipationId(participation.getId());
            item.setItemStatus(CrossDeaneryItemStatus.APPROVED);
            item.setUpdatedAt(now);
            item.setUpdatedBy(approver);
            created.add(participation);
        }
        if (created.isEmpty()) {
            return;
        }
        UnitType senderType = request.getParishId() == null ? UnitType.DEANERY : UnitType.PARISH;
        UUID senderId = request.getParishId() == null ? request.getHomeDeaneryId() : request.getParishId();
        String senderName = senderType == UnitType.PARISH
                ? parishRepository.findById(senderId).map(Parish::getName).orElse("Giáo xứ")
                : deaneryRepository.findById(senderId).map(Deanery::getName).orElse("Giáo hạt");
        courseParticipationService.announceSubmission(course, created, senderType, senderId, senderName, approver);
    }

    private void notifyRequesterOfDecision(CrossDeaneryRequest request, TrainingCourse course, String actor, int approved, int total) {
        Set<String> recipients = new LinkedHashSet<>();
        // createdBy / actor lưu mã huynh trưởng (UUID) với tài khoản gắn huynh trưởng: đổi về tên đăng nhập trước khi gửi thông báo.
        recipients.addAll(usernamesOfActor(request.getCreatedBy()));
        if (request.getParishId() != null) {
            recipients.addAll(notificationRecipientResolver.resolveRecipients(null, CourseParticipationAccessEvaluator.READ_PARISH,
                    UnitType.PARISH, request.getParishId()));
        }
        recipients.removeAll(usernamesOfActor(actor));
        if (recipients.isEmpty()) {
            return;
        }
        boolean ok = approved > 0;
        String home = deaneryRepository.findById(request.getHomeDeaneryId()).map(Deanery::getName).orElse("Giáo hạt");
        String message = ok
                ? home + " đã đồng ý " + approved + "/" + total + " hồ sơ gửi sang khóa " + courseLabel(course) + "; hồ sơ chờ " + course.getDeanery().getName() + " duyệt."
                : home + " đã từ chối yêu cầu gửi " + total + " hồ sơ sang khóa " + courseLabel(course) + (request.getDecisionNote() == null ? "." : ": " + request.getDecisionNote());
        trainingNotificationService.notifyUsers(new ArrayList<>(recipients), TrainingNotificationCommand.builder()
                .eventType(ok ? EVENT_APPROVED : EVENT_REJECTED)
                .title(ok ? "Yêu cầu gửi liên giáo hạt được đồng ý" : "Yêu cầu gửi liên giáo hạt bị từ chối")
                .message(message)
                .senderUnitType(UnitType.DEANERY)
                .senderUnitId(request.getHomeDeaneryId())
                .courseCode(course.getCourseCode())
                .totalCount(total)
                .approvedCount(approved)
                .pendingCount(0)
                .actor(actor)
                .build());
    }

    // Giáo xứ thu hồi yêu cầu: báo cho giáo hạt nhà để không còn phải xử lý yêu cầu đó.
    private void notifyHomeDeaneryOfCancel(CrossDeaneryRequest request, String actor) {
        List<String> recipients = notificationRecipientResolver.resolveRecipients(null,
                CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY, UnitType.DEANERY, request.getHomeDeaneryId());
        if (recipients.isEmpty()) {
            return;
        }
        TrainingCourse course = request.getCourse();
        String sender = request.getParishId() == null ? "Giáo hạt" : parishRepository.findById(request.getParishId()).map(Parish::getName).orElse("Giáo xứ");
        trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                .eventType(EVENT_CANCELLED)
                .title("Yêu cầu gửi liên giáo hạt đã được thu hồi")
                .message(sender + " đã thu hồi yêu cầu gửi " + request.getItems().size() + " hồ sơ sang khóa " + courseLabel(course) + ".")
                .senderUnitType(request.getParishId() == null ? UnitType.DEANERY : UnitType.PARISH)
                .senderUnitId(request.getParishId() == null ? request.getHomeDeaneryId() : request.getParishId())
                .courseCode(course.getCourseCode())
                .totalCount(request.getItems().size())
                .approvedCount(0)
                .pendingCount(0)
                .actor(actor)
                .build());
    }

    private List<String> usernamesOfActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return List.of();
        }
        try {
            return accountRepository.findActiveUsernamesByLeaderId(UUID.fromString(actor));
        } catch (IllegalArgumentException notALeaderId) {
            return List.of(actor);
        }
    }

    private Sender resolveSender(Authentication authentication) {
        if (registrationSenderResolver.available(authentication).contains(UnitType.DEANERY)) {
            UUID id = accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.SUBMIT_DEANERY);
            Deanery deanery = deaneryRepository.findById(id).orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt " + id));
            return new Sender(UnitType.DEANERY, id, null, deanery.getName(), deanery);
        }
        if (securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH)) {
            UUID id = accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH);
            Parish parish = parishRepository.findByIdWithDeaneryAndDiocese(id).orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ " + id));
            return new Sender(UnitType.PARISH, id, id, parish.getName(), parish.getDeanery());
        }
        throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gửi hồ sơ liên giáo hạt");
    }

    // Khóa đích: khóa giáo hạt của giáo hạt KHÁC cùng giáo phận, còn hoạt động và chưa kết thúc.
    private TrainingCourse requireCrossCourse(String courseCode, Sender sender) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        if (course.isPast() || !trainingWorkflowSupport.isDeaneryManaged(course) || course.getDeanery() == null) {
            throw commonValidationHelper.validationError("Chỉ gửi liên giáo hạt cho khóa của một giáo hạt");
        }
        if (course.getDeanery().getId().equals(sender.home().getId())) {
            throw commonValidationHelper.validationError("Đây là khóa của giáo hạt mình, hãy gửi ở màn Đăng ký tham dự");
        }
        UUID courseDiocese = course.getDeanery().getDiocese() == null ? null : course.getDeanery().getDiocese().getId();
        UUID homeDiocese = sender.home().getDiocese() == null ? null : sender.home().getDiocese().getId();
        if (courseDiocese == null || !courseDiocese.equals(homeDiocese)) {
            throw commonValidationHelper.forbidden("Chỉ gửi được cho khóa của giáo hạt cùng giáo phận");
        }
        // Chỉ gửi được khi giáo hạt tổ chức mở liên giáo hạt và giáo hạt nhà đã cho phép khóa này.
        if (!Boolean.TRUE.equals(course.getOpenToOtherDeaneries())) {
            throw commonValidationHelper.forbidden("Khóa này không mở đăng ký cho giáo hạt khác");
        }
        if (!accessRepository.isAllowed(courseCode, sender.home().getId())) {
            throw commonValidationHelper.forbidden("Giáo hạt của bạn chưa cho phép gửi hồ sơ sang khóa này");
        }
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
        return course;
    }

    private CrossDeaneryRequest requireRequest(String id) {
        return requestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy yêu cầu liên giáo hạt " + id));
    }

    private void requireApprover(CrossDeaneryRequest request, Authentication authentication) {
        if (!securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY)
                || !request.getHomeDeaneryId().equals(accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY))) {
            throw commonValidationHelper.forbidden("Chỉ giáo hạt của huynh trưởng mới đồng ý được yêu cầu này");
        }
    }

    // Người xem được: giáo hạt nhà (đồng ý) hoặc giáo xứ / giáo hạt đã gửi.
    private void requireVisible(CrossDeaneryRequest request, Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY)
                && request.getHomeDeaneryId().equals(accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY))) {
            return;
        }
        Sender sender = resolveSender(authentication);
        boolean own = sender.parishId() != null ? sender.parishId().equals(request.getParishId()) : sender.home().getId().equals(request.getHomeDeaneryId());
        if (!own) {
            throw commonValidationHelper.forbidden("Không có quyền xem yêu cầu này");
        }
    }

    private boolean canCancel(CrossDeaneryRequest request, Authentication authentication, String actor) {
        if (effectiveStatus(request) != CrossDeaneryStatus.PENDING) {
            return false;
        }
        if (actor != null && actor.equals(request.getCreatedBy())) {
            return true;
        }
        return request.getParishId() != null && securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH)
                && request.getParishId().equals(accountScopeValidationHelper.requireActiveParishId(authentication, CourseParticipationAccessEvaluator.SUBMIT_PARISH));
    }

    private boolean canDecide(CrossDeaneryRequest request, Authentication authentication) {
        return effectiveStatus(request) == CrossDeaneryStatus.PENDING
                && securityUtils.hasAuthority(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY)
                && request.getHomeDeaneryId().equals(accountScopeValidationHelper.requireActiveDeaneryId(authentication, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY));
    }

    // PENDING mà khóa đã hết hạn đăng ký thì coi là EXPIRED (không đồng ý được nữa).
    static CrossDeaneryStatus effectiveStatus(CrossDeaneryRequest request) {
        if (request.getRequestStatus() != CrossDeaneryStatus.PENDING) {
            return request.getRequestStatus();
        }
        TrainingCourse course = request.getCourse();
        OffsetDateTime end = course == null ? null : course.getRegistrationEndAt();
        return end != null && OffsetDateTime.now().isAfter(end) ? CrossDeaneryStatus.EXPIRED : CrossDeaneryStatus.PENDING;
    }

    // Tên đơn vị được tra một lần cho cả danh sách.
    private final class Names {
        private final Map<UUID, String> deaneries = new HashMap<>();
        private final Map<UUID, String> parishes = new HashMap<>();

        String deanery(UUID id) {
            return id == null ? null : deaneries.computeIfAbsent(id, key -> deaneryRepository.findById(key).map(Deanery::getName).orElse(null));
        }

        String parish(UUID id) {
            return id == null ? null : parishes.computeIfAbsent(id, key -> parishRepository.findById(key).map(Parish::getName).orElse(null));
        }
    }

    private RequestResponse toResponse(CrossDeaneryRequest request, Authentication authentication, Names names) {
        TrainingCourse course = request.getCourse();
        String actor = authentication == null ? null : authentication.getName();
        List<ItemResponse> items = request.getItems().stream().map(item -> {
            Leader leader = item.getLeader();
            return new ItemResponse(item.getId(), leader.getId(), leader.getHolyName(), fullName(leader),
                    leader.getParish() == null ? null : leader.getParish().getId(), leader.getParish() == null ? null : leader.getParish().getName(),
                    item.getReason(), item.getExceptionReason(), item.getNote(), item.getItemStatus(), item.getParticipationId());
        }).toList();
        return new RequestResponse(request.getId(), course.getCourseCode(), course.getCourseName(),
                course.getDeanery() == null ? null : course.getDeanery().getId(), course.getDeanery() == null ? null : course.getDeanery().getName(),
                request.getHomeDeaneryId(), names.deanery(request.getHomeDeaneryId()), request.getParishId(), names.parish(request.getParishId()),
                request.getRequesterType().name(), effectiveStatus(request), request.getDecisionNote(),
                auditActorNameResolver.resolve(request.getDecidedBy()), request.getDecidedAt(),
                auditActorNameResolver.resolve(request.getCreatedBy()), request.getCreatedAt(),
                canDecide(request, authentication), canCancel(request, authentication, actor), items, courseInfoBuilder.build(course));
    }

    private String cleanText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw commonValidationHelper.validationError(label + " tối đa " + max + " ký tự");
        }
        return trimmed;
    }

    private static String courseLabel(TrainingCourse course) {
        return course.getCourseName() == null || course.getCourseName().isBlank() ? course.getCourseCode() : course.getCourseName();
    }

    private static String fullName(Leader leader) {
        String name = leader.getLastName() == null || leader.getLastName().isBlank() ? leader.getFirstName() : leader.getLastName() + " " + leader.getFirstName();
        return Objects.toString(name, "");
    }
}
