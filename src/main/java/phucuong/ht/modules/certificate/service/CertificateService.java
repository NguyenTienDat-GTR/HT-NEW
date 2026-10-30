package phucuong.ht.modules.certificate.service;

import lombok.RequiredArgsConstructor;
import phucuong.ht.common.security.AuthorityChecks;
import phucuong.ht.common.utils.LeaderNames;
import phucuong.ht.common.repository.SearchRequests;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.certificate.model.request.CertificateBulkApprovalRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRejectRequest;
import phucuong.ht.modules.certificate.model.request.CertificateRevokeRequest;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.certificate.security.CertificateAccessEvaluator;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingBulkApprovalResponse;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource;
import phucuong.ht.modules.certificate.model.request.CertificateReissueRequest;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.TrainingDecisionNotifier trainingDecisionNotifier;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.CourseScoreVisibility courseScoreVisibility;

    private static final String DIOCESE_FILTER_FIELD = "course.diocese.id";
    private static final String DEANERY_FILTER_FIELD = "course.deanery.id";

    private final CertificateRepository certificateRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final LeaderRepository leaderRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final CertificateLifecycleService certificateLifecycleService;
    private final CourseActivityGuard courseActivityGuard;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final DomainEventPublisher domainEventPublisher;
    private final CertificateRealtimeNotifier certificateRealtimeNotifier;
    private final CertificateAccessEvaluator certificateAccessEvaluator;
    private final phucuong.ht.modules.training.trainingCourse.service.CourseFinishService courseFinishService;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    // Khóa hàng chứng nhận / huynh trưởng khi duyệt, từ chối, thu hồi: chống nhiều người thao tác cùng lúc (tuỳ chọn để test đơn vị không cần).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;

    @Cacheable(cacheNames = CacheNames.CERTIFICATE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<CertificateResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = SearchRequests.copy(request);
        if (AuthorityChecks.hasAuthority(authentication, "training.certificate.read.diocese")) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.certificate.read.diocese");
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
        } else if (AuthorityChecks.hasAuthority(authentication, "training.certificate.read.deanery")) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.certificate.read.deanery");
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem chứng chỉ");
        }
        Page<Certificate> page = certificateRepository.findAll(scopedRequest);
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    // Chứng nhận của một huynh trưởng (kể cả đã thu hồi), mới cấp nhất trước. Quyền kiểm tra ở controller theo phạm vi quản lý huynh trưởng.
    @Cacheable(cacheNames = CacheNames.CERTIFICATE_LIST, key = "@cacheScopeKeyHelper.detailKey(#authentication, 'leader-certificates:' + #leaderId)")
    public List<CertificateResponse> findByLeader(UUID leaderId, Authentication authentication) {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
        request.setSortBy("issueDate");
        request.setSortDirection("DESC");
        request.setFilters(new LinkedHashMap<>(Map.of("leader.id", leaderId)));
        // ArrayList (không phải List bất biến của Stream.toList): danh sách rỗng bất biến đọc lại từ cache Redis bị lỗi giải mã.
        return new java.util.ArrayList<>(certificateRepository.findAll(request).getContent().stream().map(this::toResponse).toList());
    }

    // Các năm cấp có chứng nhận thật trong phạm vi người xem (mới nhất trước), cho bộ lọc năm cấp:
    // không cắt theo số năm gần đây nên không mất các năm cũ, và tự có năm mới khi có chứng nhận cấp trong năm đó.
    @Transactional(readOnly = true)
    public List<Integer> issueYears(Authentication authentication) {
        UUID dioceseId = null;
        UUID deaneryId = null;
        if (AuthorityChecks.hasAuthority(authentication, "training.certificate.read.diocese")) {
            dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.certificate.read.diocese");
        } else if (AuthorityChecks.hasAuthority(authentication, "training.certificate.read.deanery")) {
            deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.certificate.read.deanery");
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem chứng chỉ");
        }
        return certificateRepository.findDistinctIssueDates(dioceseId, deaneryId).stream()
                .map(java.time.LocalDate::getYear)
                .distinct()
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.CERTIFICATE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #certificateCode)")
    public CertificateResponse findById(String certificateCode, Authentication authentication) {
        return toDetailResponse(certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode)));
    }

    // Thu hồi chứng nhận đã duyệt. Hồ sơ của khóa liên kết trở thành không đạt dù điểm cao hơn điểm đạt và huynh trưởng
    // bị hạ về cấp trước đó đã đạt. Phải thu hồi từ chứng nhận cấp cao nhất của huynh trưởng trước.
    @EvictCaches.Certificates
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public CertificateResponse revoke(String certificateCode, CertificateRevokeRequest request, Authentication authentication) {
        Certificate certificate = requireCertificate(certificateCode);
        courseActivityGuard.requireCourseEnabled(certificate.getCourse());
        String reason = commonValidationHelper.normalizeOptionalText(request == null ? null : request.getReason());
        if (reason == null) {
            throw commonValidationHelper.validationError("Phải nhập lý do khi thu hồi chứng nhận");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi chứng chỉ");
        certificateLifecycleService.revoke(certificate, reason, CertificateRevocationSource.MANUAL, true, actor, OffsetDateTime.now());
        notifyDecision(Decision.CERTIFICATE_REVOKED, List.of(certificate), Map.of(certificateCode, reason), actor);
        return toDetailResponse(certificate);
    }

    // Cấp lại chứng nhận đã bị thu hồi vì lý do khác (không phải do khóa bị vô hiệu hóa): trả về Đã duyệt, hồ sơ trở lại đạt
    // và huynh trưởng được thăng cấp lại. Phải cấp lại từ chứng nhận cấp thấp nhất trước.
    @EvictCaches.Certificates
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public CertificateResponse reissue(String certificateCode, CertificateReissueRequest request, Authentication authentication) {
        Certificate certificate = requireCertificate(certificateCode);
        courseActivityGuard.requireActive(certificate.getCourse());
        String reason = commonValidationHelper.normalizeOptionalText(request == null ? null : request.getReason());
        if (reason == null) {
            throw commonValidationHelper.validationError("Phải nhập lý do khi cấp lại chứng nhận");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cấp lại chứng chỉ");
        certificateLifecycleService.restore(certificate, reason, true, actor, OffsetDateTime.now());
        notifyDecision(Decision.CERTIFICATE_REISSUED, List.of(certificate), Map.of(certificateCode, reason), actor);
        return toDetailResponse(certificate);
    }

    private Certificate requireCertificate(String certificateCode) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode));
        lockCertificate(certificate);
        return certificate;
    }

    // Khóa hàng chứng nhận rồi nạp lại: người đến sau chờ người trước xong và thấy đúng trạng thái đã commit (đã duyệt / đã thu hồi...).
    private void lockCertificate(Certificate certificate) {
        if (entityLockService != null) {
            entityLockService.lock(certificate);
        }
    }

    // Khóa luôn huynh trưởng ngay sau chứng nhận (thứ tự thống nhất: chứng nhận rồi huynh trưởng) và TRƯỚC mọi thao tác khác trên hồ sơ của họ:
    // khóa muộn hơn thì hai giao dịch cùng giữ khóa yếu trên hàng huynh trưởng rồi cùng nâng lên khóa ghi, gây deadlock.
    private void lockLeaderOf(Certificate certificate) {
        if (entityLockService != null && certificate.getLeader() != null) {
            entityLockService.lock(certificate.getLeader());
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public CertificateResponse approve(String certificateCode, CertificateApproveRequest request, Authentication authentication) {
        Certificate certificate = approveInternal(certificateCode, request, authentication);
        notifyDecision(Decision.CERTIFICATE_APPROVED, List.of(certificate), null, certificate.getApprovedBy());
        return toResponse(certificate);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public CertificateResponse reject(String certificateCode, CertificateRejectRequest request, Authentication authentication) {
        Certificate certificate = rejectInternal(certificateCode, request == null ? null : request.getReason(), authentication);
        notifyDecision(Decision.CERTIFICATE_REJECTED, List.of(certificate), Map.of(certificateCode, certificate.getRejectionReason()), certificate.getUpdatedBy());
        return toResponse(certificate);
    }

    // Duyệt hoặc từ chối nhiều chứng nhận một lần; hồ sơ không hợp lệ được bỏ qua và trả về kèm lý do.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Leaders
    @EvictCaches.Analytics
    @Transactional
    public TrainingBulkApprovalResponse bulkApprove(CertificateBulkApprovalRequest request, Authentication authentication) {
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        List<TrainingBulkApprovalResponse.Failure> failures = new ArrayList<>();
        int success = 0;
        List<Certificate> decided = new ArrayList<>();
        Map<String, String> reasonsByCode = new HashMap<>();
        String bulkActor = null;
        // Duyệt theo thứ tự mã: hai lần duyệt hàng loạt chồng nhau khóa hàng cùng thứ tự nên không deadlock.
        for (String code : new java.util.TreeSet<>(request.getIds())) {
            Optional<Certificate> found = certificateRepository.findByCodeWithLeaderAndCourse(code);
            if (found.isEmpty()) {
                failures.add(bulkFailure(code, null, "Không tìm thấy chứng nhận"));
                continue;
            }
            String leaderName = LeaderNames.fullName(found.get().getLeader());
            if (!certificateAccessEvaluator.canApprove(authentication, code)) {
                failures.add(bulkFailure(code, leaderName, "Không có quyền duyệt chứng nhận này"));
                continue;
            }
            String own = request.getReasons() == null ? null : request.getReasons().get(code);
            String reason = own != null && !own.isBlank() ? own : request.getReason();
            try {
                if (approved) {
                    CertificateApproveRequest single = new CertificateApproveRequest();
                    single.setApprovalReason(reason);
                    single.setSignerType(request.getSignerType());
                    single.setSignerName(request.getSignerName());
                    single.setSignerTitle(request.getSignerTitle());
                    single.setApprovalDate(request.getApprovalDate());
                    Certificate done = approveInternal(code, single, authentication);
                    decided.add(done);
                    bulkActor = done.getApprovedBy();
                } else {
                    Certificate done = rejectInternal(code, reason, authentication);
                    decided.add(done);
                    reasonsByCode.put(code, done.getRejectionReason());
                    bulkActor = done.getUpdatedBy();
                }
                success++;
            } catch (RuntimeException ex) {
                failures.add(bulkFailure(code, leaderName, ex.getMessage()));
            }
        }
        // Một lần duyệt/từ chối hàng loạt: từ 3 chứng nhận cùng khóa thì người nhận chỉ có một thông báo gom nhóm.
        notifyDecision(approved ? Decision.CERTIFICATE_APPROVED : Decision.CERTIFICATE_REJECTED, decided, approved ? null : reasonsByCode, bulkActor);
        return TrainingBulkApprovalResponse.builder()
                .successCount(success)
                .failedCount(failures.size())
                .failures(failures)
                .build();
    }

    private void notifyDecision(Decision decision, List<Certificate> certificates, Map<String, String> notesByCode, String actor) {
        if (trainingDecisionNotifier != null && !certificates.isEmpty()) {
            trainingDecisionNotifier.notifyCertificates(decision, certificates, notesByCode, actor);
        }
    }

    private TrainingBulkApprovalResponse.Failure bulkFailure(String code, String leaderName, String reason) {
        return TrainingBulkApprovalResponse.Failure.builder().id(code).leaderFullName(leaderName).reason(reason).build();
    }

    private Certificate rejectInternal(String certificateCode, String rawReason, Authentication authentication) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode));
        lockCertificate(certificate);
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            throw commonValidationHelper.validationError("Chứng chỉ phải được liên kết với leader và khóa huấn luyện");
        }
        courseActivityGuard.requireActive(certificate.getCourse());
        if (certificate.getCourse().getFinishedAt() != null && !certificate.getCourse().isPast()) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã kết thúc nên không từ chối chứng nhận được, chỉ xuất được PDF");
        }
        WorkflowApprovalStatus current = certificate.getApprovalStatus();
        if (current != WorkflowApprovalStatus.PENDING && current != WorkflowApprovalStatus.SUBMITTED) {
            throw commonValidationHelper.conflict("Chỉ từ chối được chứng nhận đang chờ duyệt");
        }
        String reason = commonValidationHelper.normalizeOptionalText(rawReason);
        if (reason == null) {
            throw commonValidationHelper.validationError("Phải nhập lý do khi từ chối chứng nhận");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người từ chối chứng chỉ");
        OffsetDateTime now = OffsetDateTime.now();
        certificate.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        certificate.setRejectionReason(reason);
        certificate.setUpdatedAt(now);
        certificate.setUpdatedBy(actor);
        courseParticipationRepository.findByLeaderIdAndCourseCode(certificate.getLeader().getId(), certificate.getCourse().getCourseCode())
                .ifPresent(participation -> {
                    participation.setCertificateApprovalStatus(WorkflowApprovalStatus.REJECTED);
                    participation.setUpdatedAt(now);
                    participation.setUpdatedBy(actor);
                    courseFinishService.reevaluateIfFinished(participation, actor);
                });
        publishEvent("training.certificate.rejected", "certificate", certificateCode, actor,
                Map.of("courseCode", certificate.getCourse().getCourseCode()));
        certificateRealtimeNotifier.notifyChanged("REJECTED", certificate.getCourse().getCourseCode(), certificateCode);
        return certificate;
    }

    // Ghi người ký vào chứng nhận; bắt buộc có người ký khi duyệt.
    private void applySigner(Certificate certificate, CertificateApproveRequest request) {
        phucuong.ht.modules.certificate.model.enums.CertificateSignerType signerType = request == null ? null : request.getSignerType();
        String signerName = request == null ? null : commonValidationHelper.normalizeOptionalText(request.getSignerName());
        if (signerType == null || signerName == null) {
            throw commonValidationHelper.validationError("Phải chọn người ký khi duyệt chứng nhận");
        }
        String signerTitle;
        if (signerType == phucuong.ht.modules.certificate.model.enums.CertificateSignerType.OTHER) {
            signerTitle = request == null ? null : commonValidationHelper.normalizeOptionalText(request.getSignerTitle());
            if (signerTitle == null) {
                throw commonValidationHelper.validationError("Phải nhập chức danh người ký khi chọn 'Khác'");
            }
        } else {
            signerTitle = "Linh mục tuyên úy";
        }
        certificate.setSignerType(signerType);
        certificate.setSignerName(signerName);
        certificate.setSignerTitle(signerTitle);
        certificate.setSignedBy((signerTitle + " " + signerName).trim());
    }

    private Certificate approveInternal(String certificateCode, CertificateApproveRequest request, Authentication authentication) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode));
        // Khóa hàng trước khi kiểm tra trạng thái: hai người cùng duyệt thì người sau thấy "đã được duyệt" và bị từ chối, không ghi lịch sử cấp độ hai lần.
        lockCertificate(certificate);
        lockLeaderOf(certificate);
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            throw commonValidationHelper.validationError("Chứng chỉ phải được liên kết với leader và khóa huấn luyện");
        }
        courseActivityGuard.requireActive(certificate.getCourse());
        if (certificate.getApprovalStatus() == WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Chứng chỉ đã được duyệt trước đó");
        }
        if (certificate.getApprovalStatus() == WorkflowApprovalStatus.REVOKED) {
            throw commonValidationHelper.conflict("Chứng nhận đã bị thu hồi nên không duyệt lại được");
        }
        boolean pastCourse = certificate.getCourse().isPast();
        if (certificate.getCourse().getFinishedAt() != null && !pastCourse) {
            throw commonValidationHelper.conflict("Khóa huấn luyện đã kết thúc nên không duyệt chứng nhận được, chỉ xuất được PDF");
        }
        // Khóa quá khứ: người duyệt phải chọn ngày duyệt, không tự lấy ngày hiện tại.
        LocalDate approvalDate = pastCourse ? requirePastApprovalDate(certificate, request) : null;

        CourseParticipation participation = courseParticipationRepository.findByLeaderIdAndCourseCode(
                        certificate.getLeader().getId(),
                        certificate.getCourse().getCourseCode()
                )
                .orElseThrow(() -> commonValidationHelper.validationError("Leader không có bản ghi tham gia cho khóa huấn luyện này"));

        requireApprovedParticipationForCertificate(participation);
        // Khóa quá khứ: kết quả Đạt đã nhập từ Excel là căn cứ (điểm có thể không có).
        boolean passingScore = pastCourse
                ? participation.getResult() == phucuong.ht.modules.training.enums.CourseResult.COMPLETED
                : participation.getCourse() != null
                && participation.getTotalScore() != null
                && participation.getTotalScore().compareTo(participation.getCourse().getPassingScore()) >= 0;
        String approvalReason = request == null ? null : commonValidationHelper.normalizeOptionalText(request.getApprovalReason());
        if (!passingScore && approvalReason == null) {
            throw commonValidationHelper.validationError("approvalReason là bắt buộc khi duyệt chứng chỉ cho leader chưa đạt điểm qua");
        }

        // Người ký ghi trực tiếp vào chứng nhận khi duyệt.
        applySigner(certificate, request);

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt chứng chỉ");
        OffsetDateTime now = OffsetDateTime.now();
        // Khóa quá khứ: ngày duyệt/ngày cấp là ngày người duyệt chọn (giữa trưa giờ Việt Nam để không lệch ngày theo múi giờ).
        OffsetDateTime approvedAt = approvalDate == null ? now
                : approvalDate.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        certificate.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        certificate.setApprovedAt(approvedAt);
        certificate.setApprovedBy(actor);
        certificate.setApprovedByException(!passingScore);
        certificate.setExceptionApprovalReason(approvalReason);
        if (approvalDate != null) {
            certificate.setIssueDate(approvalDate);
        } else if (certificate.getIssueDate() == null) {
            certificate.setIssueDate(LocalDate.now());
        }
        certificate.setUpdatedAt(now);
        certificate.setUpdatedBy(actor);

        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setCertificateApprovedAt(approvedAt);
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);

        // Thăng cấp chỉ xảy ra khi kết thúc khóa; nếu khóa đã kết thúc thì đánh giá lại hồ sơ này ngay.
        courseFinishService.reevaluateIfFinished(participation, actor);

        publishEvent("training.certificate.approved", "certificate", certificateCode, actor, Map.of(
                "courseCode", certificate.getCourse().getCourseCode(),
                "approvedByException", !passingScore
        ));
        certificateRealtimeNotifier.notifyChanged("APPROVED", certificate.getCourse().getCourseCode(), certificateCode);
        return certificate;
    }

    private LocalDate requirePastApprovalDate(Certificate certificate, CertificateApproveRequest request) {
        LocalDate date = request == null ? null : request.getApprovalDate();
        if (date == null) {
            throw commonValidationHelper.validationError("Phải chọn ngày duyệt khi duyệt chứng nhận của khóa quá khứ");
        }
        if (date.isAfter(LocalDate.now())) {
            throw commonValidationHelper.validationError("Ngày duyệt không được sau ngày hiện tại");
        }
        Integer hostYear = certificate.getCourse().getHostYear();
        if (hostYear != null && date.getYear() < hostYear) {
            throw commonValidationHelper.validationError("Ngày duyệt không được trước năm tổ chức khóa (" + hostYear + ")");
        }
        return date;
    }

    private void requireApprovedParticipationForCertificate(CourseParticipation participation) {
        boolean pastCourse = participation.getCourse() != null && participation.getCourse().isPast();
        if (participation.getParticipationStatus() == phucuong.ht.modules.training.enums.ParticipationStatus.CANCELLED
                || (participation.getTotalScore() == null && !pastCourse)) {
            throw commonValidationHelper.validationError("Chứng chỉ chỉ được duyệt cho hồ sơ đã chấm điểm hợp lệ");
        }
        if (participation.getApprovalStatus() != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.validationError("Hồ sơ phải được duyệt đúng cấp trước khi duyệt chứng chỉ");
        }
    }


    private CertificateResponse toResponse(Certificate certificate) {
        return responseBuilder(certificate, findParticipation(certificate)).build();
    }

    // Chi tiết: thêm thông tin huynh trưởng, khóa, kết quả. Chỉ dùng với chứng nhận đã fetch đủ quan hệ (findByCodeWithLeaderAndCourse).
    private CertificateResponse toDetailResponse(Certificate certificate) {
        CourseParticipation participation = findParticipation(certificate);
        Leader leader = certificate.getLeader();
        TrainingCourse course = certificate.getCourse();
        var parish = leader == null ? null : leader.getParish();
        var deanery = parish == null ? null : parish.getDeanery();
        var diocese = deanery == null ? null : deanery.getDiocese();
        var courseType = course == null ? null : course.getCourseType();
        return responseBuilder(certificate, participation)
                .leaderHolyName(leader == null ? null : leader.getHolyName())
                .leaderBirthDate(leader == null ? null : leader.getBirthDate())
                .leaderParishName(parish == null ? null : parish.getName())
                .leaderDeaneryName(deanery == null ? null : deanery.getName())
                .leaderDioceseName(diocese == null ? null : diocese.getName())
                .courseTypeName(courseType == null ? null : courseType.getTypeName())
                .courseHostYear(course == null ? null : course.getHostYear())
                .courseStartDate(course == null ? null : course.getStartDate())
                .courseEndDate(course == null ? null : course.getEndDate())
                // Cấp độ đạt được: hồ sơ ngoại lệ theo cấp người duyệt đã chọn, còn lại theo loại khóa.
                .resultingLevel(participation != null && participation.getTargetLevel() != null ? participation.getTargetLevel()
                        : course == null ? null : trainingWorkflowSupport.certificateLevelFor(course).orElse(null))
                .finalResult(participation == null || participation.getFinalResult() == null ? null : participation.getFinalResult().name())
                .build();
    }

    private CourseParticipation findParticipation(Certificate certificate) {
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            return null;
        }
        return courseParticipationRepository.findByLeaderIdAndCourseCode(
                certificate.getLeader().getId(),
                certificate.getCourse().getCourseCode()
        ).orElse(null);
    }

    private CertificateResponse.CertificateResponseBuilder responseBuilder(Certificate certificate, CourseParticipation participation) {
        boolean passed = participation != null
                && participation.getTotalScore() != null
                && participation.getCourse() != null
                && participation.getTotalScore().compareTo(participation.getCourse().getPassingScore()) >= 0;
        boolean showScores = courseScoreVisibility == null || courseScoreVisibility.canSeeScores(certificate.getCourse());
        return CertificateResponse.builder()
                .certificateCode(certificate.getCertificateCode())
                .oldCertificateCode(certificate.getOldCertificateCode())
                .certificateName(certificate.getCertificateName())
                .leaderId(certificate.getLeader() == null ? null : certificate.getLeader().getId())
                .leaderHolyName(certificate.getLeader() == null ? null : certificate.getLeader().getHolyName())
                .leaderFullName(LeaderNames.fullName(certificate.getLeader()))
                .courseCode(certificate.getCourse() == null ? null : certificate.getCourse().getCourseCode())
                .courseName(certificate.getCourse() == null ? null : certificate.getCourse().getCourseName())
                .courseFinished(certificate.getCourse() != null && certificate.getCourse().getFinishedAt() != null)
                .coursePast(certificate.getCourse() != null && certificate.getCourse().isPast())
                .scoreHidden(!showScores)
                .totalScore(participation == null || !showScores ? null : participation.getTotalScore())
                .passingScore(participation == null || participation.getCourse() == null ? null : participation.getCourse().getPassingScore())
                .scoreNote(participation == null || !showScores ? null : participation.getScoreNote())
                .passed(participation == null || participation.getTotalScore() == null ? null : passed)
                .approvalReasonRequired(participation != null && participation.getTotalScore() != null && !passed)
                .result(certificate.getResult())
                .signedBy(certificate.getSignedBy())
                .signerType(certificate.getSignerType())
                .signerName(certificate.getSignerName())
                .signerTitle(certificate.getSignerTitle())
                .issueDate(certificate.getIssueDate())
                .imageUrl(certificate.getImageUrl())
                .approvalStatus(certificate.getApprovalStatus())
                .systemSuggested(certificate.getSystemSuggested())
                .approvedByException(certificate.getApprovedByException())
                .exceptionApprovalReason(certificate.getExceptionApprovalReason())
                .rejectionReason(certificate.getRejectionReason())
                .approvedAt(certificate.getApprovedAt())
                .approvedBy(certificate.getApprovedBy())
                .approvedByName(auditActorNameResolver.resolve(certificate.getApprovedBy()))
                .revokedAt(certificate.getRevokedAt())
                .revokedBy(certificate.getRevokedBy())
                .revokedByName(auditActorNameResolver.resolve(certificate.getRevokedBy()))
                .revocationReason(certificate.getRevocationReason())
                .revocationSource(certificate.getRevocationSource() == null ? null : certificate.getRevocationSource().name())
                .reissuedAt(certificate.getReissuedAt())
                .reissuedBy(certificate.getReissuedBy())
                .reissuedByName(auditActorNameResolver.resolve(certificate.getReissuedBy()))
                .reissueReason(certificate.getReissueReason())
                .status(certificate.getStatus())
                .createdAt(certificate.getCreatedAt())
                .createdBy(certificate.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(certificate.getCreatedBy()))
                .updatedAt(certificate.getUpdatedAt())
                .updatedBy(certificate.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(certificate.getUpdatedBy()));
    }

    private void publishEvent(String eventType, String entityType, String entityId, String actor, Map<String, Object> payload) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .occurredAt(OffsetDateTime.now())
                .payload(payload)
                .build());
    }
}
