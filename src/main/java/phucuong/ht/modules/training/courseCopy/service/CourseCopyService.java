package phucuong.ht.modules.training.courseCopy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingDecisionNotifier;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.AnySource;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.Candidate;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyRequest;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyResult;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyToCourseRequest;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.Skipped;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.SourceCourse;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.SourcesResponse;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sao chép danh sách tham dự của các khóa tiên quyết đã kết thúc (đã chốt kết quả chung) sang khóa huấn luyện.
 * Hồ sơ được sao chép vào thẳng danh sách tham dự (đã duyệt, không kiểm tra điều kiện của khóa), thông báo như khi duyệt đăng ký
 * (không tự gửi email thông tin tham dự dù khóa bật tự gửi). Huynh trưởng đã có hồ sơ trong khóa (mọi trạng thái) được bỏ qua.
 */
@Service
@RequiredArgsConstructor
public class CourseCopyService {

    static final String BLOCK_EXISTING = "Đã có hồ sơ trong khóa";
    static final String BLOCK_INACTIVE = "Huynh trưởng đã ngưng hoạt động";
    static final String BLOCK_PARISH_LOCKED = "Giáo xứ đang bị khóa";

    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final CourseParticipationService courseParticipationService;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CourseActivityGuard courseActivityGuard;
    private final CommonValidationHelper commonValidationHelper;
    // Dùng cho sao chép khi tạo / sửa khóa (field injection để không đổi constructor của luồng sao chép khóa tiên quyết).
    @Autowired
    private phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository trainingCourseTypeRepository;
    @Autowired
    private phucuong.ht.common.validation.AccountScopeValidationHelper accountScopeValidationHelper;
    @Autowired
    private phucuong.ht.modules.deanery.repository.DeaneryRepository deaneryRepository;
    @Autowired
    private phucuong.ht.common.utils.securityUtils securityUtils;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private TrainingDecisionNotifier trainingDecisionNotifier;
    @Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;

    @Transactional(readOnly = true)
    public SourcesResponse sources(String courseCode) {
        TrainingCourse course = requireCourse(courseCode);
        TrainingCourseType prerequisite = course.isPast() ? null : trainingWorkflowSupport.prerequisiteOf(course.getCourseType()).orElse(null);
        if (prerequisite == null) {
            return new SourcesResponse(null, false, List.of());
        }
        List<TrainingCourse> sources = sourceCoursesOf(course, prerequisite);
        Map<String, long[]> counts = new HashMap<>();
        for (CourseParticipation participation : courseParticipationRepository.findApprovedByCourseCodes(
                sources.stream().map(TrainingCourse::getCourseCode).toList())) {
            long[] count = counts.computeIfAbsent(participation.getCourse().getCourseCode(), key -> new long[2]);
            count[0]++;
            if (participation.getFinalResult() == CourseFinalResult.PASSED) {
                count[1]++;
            }
        }
        List<SourceCourse> items = sources.stream().map(source -> {
            long[] count = counts.getOrDefault(source.getCourseCode(), new long[2]);
            return new SourceCourse(source.getCourseCode(), source.getCourseName(), source.getHostYear(), source.getStartDate(),
                    source.getEndDate(), source.getFinishedAt(), ownerName(source), count[0], count[1]);
        }).toList();
        return new SourcesResponse(prerequisite.getTypeName(), isWritable(course), items);
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(String courseCode, List<String> sourceCourseCodes, boolean includeFailed) {
        TrainingCourse course = requireCourse(courseCode);
        return new ArrayList<>(collectCandidates(course, sourceCourseCodes, includeFailed).values());
    }

    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public CopyResult copy(String courseCode, CopyRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(courseCode);
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
        requireWritable(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người sao chép danh sách tham dự");
        Map<UUID, Candidate> candidates = collectCandidates(course, request.sourceCourseCodes(), request.includeFailed());
        return persistCopies(course, request.sourceCourseCodes(), candidates, request.leaderIds(), actor);
    }

    private CopyResult persistCopies(TrainingCourse course, List<String> sourceCourseCodes, Map<UUID, Candidate> candidates,
                                     Collection<UUID> leaderIds, String actor) {
        // Hồ sơ gốc ở khóa nguồn đã chọn cho từng huynh trưởng (để lấy huynh trưởng và người gửi đăng ký gốc).
        Map<UUID, CourseParticipation> sources = new HashMap<>();
        for (CourseParticipation participation : courseParticipationRepository.findApprovedByCourseCodes(sourceCourseCodes)) {
            Candidate candidate = candidates.get(participation.getLeader().getId());
            if (candidate != null && candidate.sourceCourseCode().equals(participation.getCourse().getCourseCode())) {
                sources.put(candidate.leaderId(), participation);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<CourseParticipation> created = new ArrayList<>();
        Map<String, String> submitterById = new HashMap<>();
        List<Skipped> skipped = new ArrayList<>();
        for (UUID leaderId : new LinkedHashSet<>(leaderIds)) {
            Candidate candidate = candidates.get(leaderId);
            if (candidate == null) {
                skipped.add(new Skipped(leaderId, null, "Không thuộc danh sách tham dự của khóa tiên quyết đã chọn"));
                continue;
            }
            if (candidate.blockedReason() != null) {
                skipped.add(new Skipped(leaderId, candidate.fullName(), candidate.blockedReason()));
                continue;
            }
            CourseParticipation source = sources.get(leaderId);
            CourseParticipation participation = newParticipation(course, source.getLeader(), actor, now, candidate.sourceCourseName());
            courseParticipationRepository.save(participation);
            created.add(participation);
            submitterById.put(participation.getId(), source.getCreatedBy());
        }
        if (!created.isEmpty()) {
            // Không tự gửi email thông tin tham dự khi sao chép (dù khóa bật tự gửi): người quản lý chủ động gửi sau ở tab Thẻ & điểm danh.
            // Chỉ thông báo trong hệ thống cho người gửi đăng ký gốc / admin giáo xứ / huynh trưởng.
            if (trainingDecisionNotifier != null) {
                trainingDecisionNotifier.notifyRegistrations(Decision.REGISTRATION_APPROVED, created, null, actor, submitterById);
            }
        }
        return new CopyResult(created.size(), skipped);
    }

    /**
     * Khóa nguồn chọn được khi tạo / sửa khóa: khóa thường đã kết thúc hoặc khóa quá khứ cùng giáo phận (khóa giáo hạt chỉ nhận khóa của chính giáo hạt đó
     * và khóa cấp giáo phận). Sửa khóa thì truyền courseCode; tạo khóa thì truyền courseTypeCode (phạm vi lấy theo tài khoản).
     */
    @Transactional(readOnly = true)
    public List<AnySource> anySources(String courseCode, String courseTypeCode, Authentication authentication) {
        TargetScope scope = resolveScope(courseCode, courseTypeCode, authentication);
        List<TrainingCourse> sources = copySourcesIn(scope.dioceseId(), scope.deaneryManaged(), scope.deaneryId(), scope.courseCode());
        Map<String, long[]> counts = courseParticipationRepository.countAttendeesAndPassedByCourseCodes(
                sources.stream().map(TrainingCourse::getCourseCode).toList());
        return sources.stream().map(source -> {
            long[] count = counts.getOrDefault(source.getCourseCode(), new long[2]);
            return new AnySource(source.getCourseCode(), source.getCourseName(), source.getHostYear(),
                    source.getCourseType() == null ? null : source.getCourseType().getTypeName(), source.isPast(),
                    source.getStartDate(), source.getEndDate(), ownerName(source), count[0], count[1]);
        }).toList();
    }

    // Phạm vi của khóa đích: khóa đã có (sửa khóa) hoặc phạm vi của tài khoản theo loại khóa (tạo khóa).
    private record TargetScope(UUID dioceseId, UUID deaneryId, boolean deaneryManaged, String courseCode) {
    }

    private TargetScope resolveScope(String courseCode, String courseTypeCode, Authentication authentication) {
        if (courseCode != null && !courseCode.isBlank()) {
            TrainingCourse course = requireCourse(courseCode);
            return new TargetScope(trainingWorkflowSupport.dioceseIdOf(course), course.getDeanery() == null ? null : course.getDeanery().getId(),
                    trainingWorkflowSupport.isDeaneryManaged(course), course.getCourseCode());
        }
        TrainingCourseType type = trainingCourseTypeRepository.findByCodeWithPrerequisite(courseTypeCode == null ? "" : courseTypeCode.trim())
                .orElseThrow(() -> commonValidationHelper.validationError("Chưa chọn loại khóa huấn luyện"));
        if (trainingWorkflowSupport.isDeaneryManaged(type)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.CREATE_DEANERY);
            return new TargetScope(deaneryRepository.getRequired(deaneryId).getDiocese().getId(), deaneryId, true, null);
        }
        UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_DIOCESE);
        return new TargetScope(dioceseId, null, false, null);
    }

    /**
     * Hồ sơ trong danh sách tham dự của các khóa nguồn đã chọn, để người dùng chọn từng huynh trưởng cần sao chép (khóa đích đã có hoặc chưa tạo).
     * Trùng ở nhiều khóa nguồn thì lấy hồ sơ đạt (khóa kết thúc gần nhất); blockedReason khác null = không sao chép được.
     */
    @Transactional(readOnly = true)
    public List<Candidate> anyCandidates(String courseCode, String courseTypeCode, List<String> sourceCourseCodes, Authentication authentication) {
        TargetScope scope = resolveScope(courseCode, courseTypeCode, authentication);
        Set<String> requested = new LinkedHashSet<>(sourceCourseCodes == null ? List.of() : sourceCourseCodes);
        if (requested.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = copySourcesIn(scope.dioceseId(), scope.deaneryManaged(), scope.deaneryId(), scope.courseCode())
                .stream().map(TrainingCourse::getCourseCode).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!allowed.containsAll(requested)) {
            throw commonValidationHelper.validationError("Khóa nguồn đã chọn không hợp lệ hoặc không thuộc phạm vi của khóa");
        }
        Map<UUID, CourseParticipation> best = new LinkedHashMap<>();
        for (CourseParticipation participation : courseParticipationRepository.findApprovedByCourseCodes(requested)) {
            if (withinScope(scope, participation.getLeader().getParish())) {
                best.merge(participation.getLeader().getId(), participation, CourseCopyService::preferred);
            }
        }
        Set<UUID> existing = scope.courseCode() == null ? Set.of() : courseParticipationRepository.findLeaderIdsByCourseCode(scope.courseCode());
        Map<UUID, Boolean> parishOperational = new HashMap<>();
        return best.values().stream()
                .sorted(Comparator.comparing((CourseParticipation item) -> deaneryName(item.getLeader()), Comparator.nullsLast(String::compareTo))
                        .thenComparing(item -> item.getLeader().getParish().getName(), Comparator.nullsLast(String::compareTo))
                        .thenComparing(item -> Objects.toString(item.getLeader().getFirstName(), ""))
                        .thenComparing(item -> Objects.toString(item.getLeader().getLastName(), "")))
                .map(item -> toCandidate(item, null, blockedReason(item.getLeader(), existing, parishOperational)))
                .toList();
    }

    private static boolean withinScope(TargetScope scope, Parish parish) {
        if (parish == null || parish.getDeanery() == null) {
            return false;
        }
        if (scope.deaneryManaged()) {
            return scope.deaneryId() != null && scope.deaneryId().equals(parish.getDeanery().getId());
        }
        return scope.dioceseId() != null && parish.getDeanery().getDiocese() != null && scope.dioceseId().equals(parish.getDeanery().getDiocese().getId());
    }

    /**
     * Sao chép danh sách tham dự từ các khóa nguồn vào khóa vừa tạo / vừa sửa (cùng giao dịch với việc lưu khóa).
     * passedOnly = chỉ hồ sơ có kết quả đạt; ngược lại sao chép mọi hồ sơ. Hồ sơ không sao chép được (đã có trong khóa, ngưng hoạt động...) bị bỏ qua.
     */
    @Transactional
    public CopyResult copyFromSources(TrainingCourse course, List<String> sourceCourseCodes, boolean passedOnly, Authentication authentication) {
        return copyFromSources(course, sourceCourseCodes, passedOnly, null, authentication);
    }

    // Sao chép ngay vào khóa đã có (nút Sao chép ở tab Danh sách tham dự): quyền kiểm tra ở controller, quyền duyệt đăng ký kiểm tra lại trong copyFromSources.
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public CopyResult copyToCourse(String courseCode, CopyToCourseRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(courseCode);
        return copyFromSources(course, request.sourceCourseCodes(), "PASSED_ONLY".equals(request.copyMode()), request.leaderIds(), authentication);
    }

    /** onlyLeaderIds khác rỗng: chỉ sao chép các huynh trưởng được chọn riêng (bỏ qua passedOnly); huynh trưởng không thuộc khóa nguồn bị báo bỏ qua. */
    @Transactional
    public CopyResult copyFromSources(TrainingCourse course, List<String> sourceCourseCodes, boolean passedOnly, List<UUID> onlyLeaderIds,
                                      Authentication authentication) {
        if (sourceCourseCodes == null || sourceCourseCodes.isEmpty()) {
            return new CopyResult(0, List.of());
        }
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
        requireWritable(course);
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        String approveAuthority = deaneryManaged ? CourseParticipationAccessEvaluator.APPROVE_DEANERY : CourseParticipationAccessEvaluator.APPROVE_DIOCESE;
        if (!securityUtils.hasAuthority(authentication, approveAuthority)) {
            throw commonValidationHelper.forbidden("Cần quyền duyệt đăng ký của khóa để sao chép danh sách tham dự");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người sao chép danh sách tham dự");
        Set<String> allowed = copySourcesIn(trainingWorkflowSupport.dioceseIdOf(course), deaneryManaged,
                course.getDeanery() == null ? null : course.getDeanery().getId(), course.getCourseCode())
                .stream().map(TrainingCourse::getCourseCode).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean picked = onlyLeaderIds != null && !onlyLeaderIds.isEmpty();
        Map<UUID, Candidate> candidates = collectCandidates(course, sourceCourseCodes, picked || !passedOnly, allowed,
                "Chưa chọn khóa nguồn nào", "Khóa nguồn đã chọn không hợp lệ hoặc không thuộc phạm vi của khóa");
        List<Skipped> skipped = new ArrayList<>();
        if (picked) {
            Set<UUID> wanted = new LinkedHashSet<>(onlyLeaderIds);
            for (UUID leaderId : wanted) {
                if (!candidates.containsKey(leaderId)) {
                    skipped.add(new Skipped(leaderId, null, "Không thuộc danh sách tham dự của khóa nguồn đã chọn"));
                }
            }
            candidates = candidates.entrySet().stream().filter(entry -> wanted.contains(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
        List<UUID> leaderIds = candidates.values().stream().filter(candidate -> candidate.blockedReason() == null).map(Candidate::leaderId).toList();
        // Hồ sơ không sao chép được cũng được báo lại (bỏ qua) để người dùng biết.
        skipped.addAll(candidates.values().stream().filter(candidate -> candidate.blockedReason() != null)
                .map(candidate -> new Skipped(candidate.leaderId(), candidate.fullName(), candidate.blockedReason())).toList());
        if (leaderIds.isEmpty()) {
            return new CopyResult(0, skipped);
        }
        CopyResult copied = persistCopies(course, sourceCourseCodes, candidates, leaderIds, actor);
        skipped.addAll(copied.skipped());
        return new CopyResult(copied.copied(), skipped);
    }

    // Khóa nguồn trong giáo phận: khóa giáo hạt chỉ nhận khóa của chính giáo hạt đó và khóa cấp giáo phận; khóa giáo phận nhận mọi khóa trong giáo phận.
    private List<TrainingCourse> copySourcesIn(UUID dioceseId, boolean deaneryManaged, UUID deaneryId, String excludedCourseCode) {
        if (dioceseId == null) {
            return List.of();
        }
        return trainingCourseRepository.findCopySources(dioceseId).stream()
                .filter(source -> !source.getCourseCode().equals(excludedCourseCode))
                .filter(source -> !deaneryManaged || source.getDeanery() == null
                        || (deaneryId != null && deaneryId.equals(source.getDeanery().getId())))
                .toList();
    }

    // Hồ sơ vào thẳng danh sách tham dự như hồ sơ đã được duyệt; điểm được chấm sau ở tab Chấm điểm.
    private CourseParticipation newParticipation(TrainingCourse course, Leader leader, String actor, OffsetDateTime now, String sourceName) {
        CourseParticipation participation = new CourseParticipation();
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setEligible(Boolean.TRUE);
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setApprovedBy(actor);
        participation.setRegistrationDate(LocalDate.now());
        participation.setResult(CourseResult.UNCOMPLETED);
        participation.setParticipationStatus(ParticipationStatus.ATTENDED);
        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
        participation.setReview(sourceName == null ? "Sao chép từ khóa tiên quyết" : "Sao chép từ danh sách tham dự khóa \"" + sourceName + "\"");
        participation.setStatus(Boolean.TRUE);
        participation.setCreatedAt(now);
        participation.setUpdatedAt(now);
        participation.setCreatedBy(actor);
        participation.setUpdatedBy(actor);
        return participation;
    }

    // Gộp hồ sơ của các khóa nguồn theo huynh trưởng; trùng thì ưu tiên hồ sơ đạt rồi đến khóa kết thúc gần nhất.
    private Map<UUID, Candidate> collectCandidates(TrainingCourse course, List<String> sourceCourseCodes, boolean includeFailed) {
        TrainingCourseType prerequisite = course.isPast() ? null : trainingWorkflowSupport.prerequisiteOf(course.getCourseType()).orElse(null);
        if (prerequisite == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện không có khóa tiên quyết để sao chép danh sách tham dự");
        }
        Set<String> allowed = new LinkedHashSet<>(sourceCoursesOf(course, prerequisite).stream().map(TrainingCourse::getCourseCode).toList());
        return collectCandidates(course, sourceCourseCodes, includeFailed, allowed, "Chưa chọn khóa tiên quyết nào",
                "Khóa tiên quyết đã chọn không hợp lệ hoặc chưa kết thúc");
    }

    private Map<UUID, Candidate> collectCandidates(TrainingCourse course, List<String> sourceCourseCodes, boolean includeFailed,
                                                   Set<String> allowed, String emptyMessage, String invalidMessage) {
        Set<String> requested = new LinkedHashSet<>(sourceCourseCodes == null ? List.of() : sourceCourseCodes);
        if (requested.isEmpty()) {
            throw commonValidationHelper.validationError(emptyMessage);
        }
        if (!allowed.containsAll(requested)) {
            throw commonValidationHelper.validationError(invalidMessage);
        }

        Map<UUID, CourseParticipation> best = new LinkedHashMap<>();
        for (CourseParticipation participation : courseParticipationRepository.findApprovedByCourseCodes(requested)) {
            boolean passed = participation.getFinalResult() == CourseFinalResult.PASSED;
            if (!passed && !includeFailed) {
                continue;
            }
            if (!withinCourseScope(course, participation.getLeader().getParish())) {
                continue;
            }
            best.merge(participation.getLeader().getId(), participation, CourseCopyService::preferred);
        }

        Set<UUID> existing = courseParticipationRepository.findLeaderIdsByCourseCode(course.getCourseCode());
        Map<UUID, Boolean> parishOperational = new HashMap<>();
        Map<UUID, Candidate> result = new LinkedHashMap<>();
        best.values().stream()
                .sorted(Comparator.comparing((CourseParticipation item) -> deaneryName(item.getLeader()), Comparator.nullsLast(String::compareTo))
                        .thenComparing(item -> item.getLeader().getParish().getName(), Comparator.nullsLast(String::compareTo))
                        .thenComparing(item -> Objects.toString(item.getLeader().getFirstName(), ""))
                        .thenComparing(item -> Objects.toString(item.getLeader().getLastName(), "")))
                .forEach(item -> {
                    Leader leader = item.getLeader();
                    String blocked = blockedReason(leader, existing, parishOperational);
                    result.put(leader.getId(), toCandidate(item, course, blocked));
                });
        return result;
    }

    private static CourseParticipation preferred(CourseParticipation current, CourseParticipation other) {
        boolean currentPassed = current.getFinalResult() == CourseFinalResult.PASSED;
        boolean otherPassed = other.getFinalResult() == CourseFinalResult.PASSED;
        if (currentPassed != otherPassed) {
            return currentPassed ? current : other;
        }
        OffsetDateTime currentAt = current.getCourse().getFinishedAt();
        OffsetDateTime otherAt = other.getCourse().getFinishedAt();
        if (currentAt == null) {
            return other;
        }
        return otherAt != null && otherAt.isAfter(currentAt) ? other : current;
    }

    private String blockedReason(Leader leader, Set<UUID> existing, Map<UUID, Boolean> parishOperational) {
        if (existing.contains(leader.getId())) {
            return BLOCK_EXISTING;
        }
        if (Boolean.FALSE.equals(leader.getStatus())) {
            return BLOCK_INACTIVE;
        }
        if (unitOperationalStatusService != null && leader.getParish() != null) {
            boolean operational = parishOperational.computeIfAbsent(leader.getParish().getId(),
                    id -> unitOperationalStatusService.isUnitOperational(UnitType.PARISH, id));
            if (!operational) {
                return BLOCK_PARISH_LOCKED;
            }
        }
        return null;
    }

    private Candidate toCandidate(CourseParticipation source, TrainingCourse course, String blocked) {
        Leader leader = source.getLeader();
        Parish parish = leader.getParish();
        boolean eligible = course != null && blocked == null && courseParticipationService.isEligible(leader.getLeaderLevel(), leader.getBirthDate(), leader.getId(), course);
        return new Candidate(
                leader.getId(),
                leader.getHolyName(),
                fullName(leader),
                leader.getGender() == null ? null : leader.getGender().name(),
                leader.getLeaderLevel(),
                parish.getDeanery() == null ? null : parish.getDeanery().getId(),
                deaneryName(leader),
                parish.getId(),
                parish.getName(),
                source.getCourse().getCourseCode(),
                source.getCourse().getCourseName(),
                source.getFinalResult() == CourseFinalResult.PASSED ? CourseFinalResult.PASSED : CourseFinalResult.FAILED,
                eligible,
                blocked
        );
    }

    // Khóa nguồn: khóa đã kết thúc thuộc loại tiên quyết trong phạm vi khóa đích.
    // Khóa giáo hạt: khóa của chính giáo hạt đó hoặc của giáo phận chứa nó; khóa giáo phận: mọi khóa trong giáo phận.
    private List<TrainingCourse> sourceCoursesOf(TrainingCourse course, TrainingCourseType prerequisite) {
        UUID dioceseId = trainingWorkflowSupport.dioceseIdOf(course);
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        return trainingCourseRepository.findFinishedByCourseTypeCode(prerequisite.getTypeCode()).stream()
                .filter(source -> !source.getCourseCode().equals(course.getCourseCode()))
                .filter(source -> {
                    if (deaneryManaged && source.getDeanery() != null) {
                        return course.getDeanery() != null && course.getDeanery().getId().equals(source.getDeanery().getId());
                    }
                    return dioceseId != null && dioceseId.equals(trainingWorkflowSupport.dioceseIdOf(source));
                })
                .toList();
    }

    // Huynh trưởng phải thuộc phạm vi khóa đích (giáo hạt sở hữu khóa giáo hạt / giáo phận sở hữu khóa giáo phận).
    private boolean withinCourseScope(TrainingCourse course, Parish parish) {
        if (parish == null || parish.getDeanery() == null) {
            return false;
        }
        if (trainingWorkflowSupport.isDeaneryManaged(course)) {
            return course.getDeanery() != null && course.getDeanery().getId().equals(parish.getDeanery().getId());
        }
        UUID dioceseId = trainingWorkflowSupport.dioceseIdOf(course);
        return dioceseId != null && parish.getDeanery().getDiocese() != null && dioceseId.equals(parish.getDeanery().getDiocese().getId());
    }

    private boolean isWritable(TrainingCourse course) {
        return !course.isPast() && !Boolean.FALSE.equals(course.getStatus()) && !courseActivityGuard.isEnded(course);
    }

    private void requireWritable(TrainingCourse course) {
        if (course.isPast()) {
            throw commonValidationHelper.validationError("Khóa quá khứ không sao chép được danh sách tham dự");
        }
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
    }

    private TrainingCourse requireCourse(String courseCode) {
        return trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
    }

    private static String ownerName(TrainingCourse course) {
        if (course.getDeanery() != null) {
            return course.getDeanery().getName();
        }
        return course.getDiocese() == null ? null : course.getDiocese().getName();
    }

    private static String deaneryName(Leader leader) {
        return leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getName();
    }

    private static String fullName(Leader leader) {
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }
}
