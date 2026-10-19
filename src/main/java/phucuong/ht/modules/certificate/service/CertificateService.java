package phucuong.ht.modules.certificate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.request.CertificateApproveRequest;
import phucuong.ht.modules.certificate.model.request.CertificateCreateRequest;
import phucuong.ht.modules.certificate.model.request.CertificateUpdateRequest;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final String DIOCESE_FILTER_FIELD = "course.diocese.id";
    private static final String DEANERY_FILTER_FIELD = "course.deanery.id";

    private final CertificateRepository certificateRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final LeaderRepository leaderRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final RankHistoryRepository rankHistoryRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final DomainEventPublisher domainEventPublisher;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @Transactional
    public CertificateResponse create(CertificateCreateRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(request.getCourseCode());
        Leader leader = requireLeader(request.getLeaderId());
        requireLeaderInCourseScope(leader, course);
        if (certificateRepository.findByLeaderIdAndCourseCode(leader.getId(), course.getCourseCode()).isPresent()) {
            throw commonValidationHelper.conflict("Leader đã có chứng chỉ cho khóa huấn luyện này");
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo chứng chỉ");
        OffsetDateTime now = OffsetDateTime.now();

        Certificate certificate = new Certificate();
        certificate.setCertificateCode(request.getCertificateCode());
        certificate.setOldCertificateCode(request.getOldCertificateCode());
        certificate.setCertificateName(request.getCertificateName());
        certificate.setLeader(leader);
        certificate.setCourse(course);
        certificate.setResult(request.getResult());
        certificate.setSignedBy(request.getSignedBy());
        certificate.setIssueDate(request.getIssueDate());
        certificate.setImageUrl(request.getImageUrl());
        certificate.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        certificate.setSystemSuggested(Boolean.FALSE);
        certificate.setApprovedByException(Boolean.FALSE);
        certificate.setStatus(Boolean.TRUE);
        certificate.setCreatedAt(now);
        certificate.setUpdatedAt(now);
        certificate.setCreatedBy(actor);
        certificate.setUpdatedBy(actor);
        certificateRepository.save(certificate);
        return toResponse(certificate);
    }

    @Cacheable(cacheNames = CacheNames.CERTIFICATE_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<CertificateResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        if (hasAuthority(authentication, "training.certificate.read.diocese")) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, "training.certificate.read.diocese");
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
        } else if (hasAuthority(authentication, "training.certificate.read.deanery")) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, "training.certificate.read.deanery");
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
        } else {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem chứng chỉ");
        }
        Page<Certificate> page = certificateRepository.findAll(scopedRequest);
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    @Cacheable(cacheNames = CacheNames.CERTIFICATE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #certificateCode)")
    public CertificateResponse findById(String certificateCode, Authentication authentication) {
        return toResponse(certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @Transactional
    public CertificateResponse update(String certificateCode, CertificateUpdateRequest request, Authentication authentication) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode));
        if (certificate.getApprovalStatus() == WorkflowApprovalStatus.APPROVED
                && (request.getLeaderId() != null || request.getCourseCode() != null || request.getResult() != null)) {
            throw commonValidationHelper.conflict("Không thể thay đổi leader, khóa học hoặc kết quả của chứng chỉ đã duyệt");
        }

        Leader targetLeader = request.getLeaderId() == null ? certificate.getLeader() : requireLeader(request.getLeaderId());
        TrainingCourse targetCourse = request.getCourseCode() == null ? certificate.getCourse() : requireCourse(request.getCourseCode());
        if (request.getLeaderId() != null || request.getCourseCode() != null) {
            requireLeaderInCourseScope(targetLeader, targetCourse);
            requireUpdateAuthorityForTargetCourse(targetCourse, authentication);
        }

        if (request.getOldCertificateCode() != null) {
            certificate.setOldCertificateCode(request.getOldCertificateCode());
        }
        if (request.getCertificateName() != null) {
            certificate.setCertificateName(request.getCertificateName());
        }
        if (request.getLeaderId() != null) {
            certificate.setLeader(targetLeader);
        }
        if (request.getCourseCode() != null) {
            certificate.setCourse(targetCourse);
        }
        requireLeaderInCourseScope(certificate.getLeader(), certificate.getCourse());
        if (request.getResult() != null) {
            certificate.setResult(request.getResult());
        }
        if (request.getSignedBy() != null) {
            certificate.setSignedBy(request.getSignedBy());
        }
        if (request.getIssueDate() != null) {
            certificate.setIssueDate(request.getIssueDate());
        }
        if (request.getImageUrl() != null) {
            certificate.setImageUrl(request.getImageUrl());
        }
        certificate.setUpdatedAt(OffsetDateTime.now());
        certificate.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật chứng chỉ"));
        return toResponse(certificate);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    public CertificateResponse toggleStatus(String certificateCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        certificateRepository.toggleStatus(certificateCode, commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái chứng chỉ"));
        return findById(certificateCode, authentication);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @Transactional
    public CertificateResponse approve(String certificateCode, CertificateApproveRequest request, Authentication authentication) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chứng chỉ: " + certificateCode));
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            throw commonValidationHelper.validationError("Chứng chỉ phải được liên kết với leader và khóa huấn luyện");
        }
        if (certificate.getApprovalStatus() == WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Chứng chỉ đã được duyệt trước đó");
        }

        CourseParticipation participation = courseParticipationRepository.findByLeaderIdAndCourseCode(
                        certificate.getLeader().getId(),
                        certificate.getCourse().getCourseCode()
                )
                .orElseThrow(() -> commonValidationHelper.validationError("Leader không có bản ghi tham gia cho khóa huấn luyện này"));

        requireApprovedParticipationForCertificate(participation);
        boolean passingScore = participation.getCourse() != null
                && participation.getTotalScore() != null
                && participation.getTotalScore().compareTo(participation.getCourse().getPassingScore()) >= 0;
        String approvalReason = request == null ? null : commonValidationHelper.normalizeOptionalText(request.getApprovalReason());
        if (!passingScore && approvalReason == null) {
            throw commonValidationHelper.validationError("approvalReason là bắt buộc khi duyệt chứng chỉ cho leader chưa đạt điểm qua");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người duyệt chứng chỉ");
        OffsetDateTime now = OffsetDateTime.now();
        certificate.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        certificate.setApprovedAt(now);
        certificate.setApprovedBy(actor);
        certificate.setApprovedByException(!passingScore);
        certificate.setExceptionApprovalReason(approvalReason);
        if (certificate.getIssueDate() == null) {
            certificate.setIssueDate(LocalDate.now());
        }
        certificate.setUpdatedAt(now);
        certificate.setUpdatedBy(actor);

        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setCertificateApprovedAt(now);
        participation.setApprovedBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);

        trainingWorkflowSupport.certificateLevelFor(certificate.getCourse().getCourseType())
                .filter(newLevel -> trainingWorkflowSupport.isPromotion(certificate.getLeader().getLeaderLevel(), newLevel))
                .ifPresent(newLevel -> promoteLeader(certificate.getLeader(), newLevel, actor, certificateCode));

        publishEvent("training.certificate.approved", "certificate", certificateCode, actor, Map.of(
                "courseCode", certificate.getCourse().getCourseCode(),
                "approvedByException", !passingScore
        ));
        return toResponse(certificate);
    }

    private void requireApprovedParticipationForCertificate(CourseParticipation participation) {
        if (participation.getParticipationStatus() == phucuong.ht.modules.training.enums.ParticipationStatus.CANCELLED
                || participation.getTotalScore() == null) {
            throw commonValidationHelper.validationError("Chứng chỉ chỉ được duyệt cho hồ sơ đã chấm điểm hợp lệ");
        }
        if (participation.getApprovalStatus() != WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.validationError("Hồ sơ phải được duyệt đúng cấp trước khi duyệt chứng chỉ");
        }
    }

    private void promoteLeader(Leader leader, LeaderLevel newLevel, String actor, String certificateCode) {
        if (leader.getLeaderLevel() == newLevel) {
            return;
        }

        RankHistory rankHistory = new RankHistory();
        OffsetDateTime now = OffsetDateTime.now();
        rankHistory.setLeader(leader);
        rankHistory.setOldLevel(leader.getLeaderLevel());
        rankHistory.setNewLevel(newLevel);
        rankHistory.setPromotionDate(LocalDate.now());
        rankHistory.setNote("Duyệt từ chứng chỉ " + certificateCode);
        rankHistory.setStatus(Boolean.TRUE);
        rankHistory.setCreatedAt(now);
        rankHistory.setUpdatedAt(now);
        rankHistory.setCreatedBy(actor);
        rankHistory.setUpdatedBy(actor);
        rankHistoryRepository.save(rankHistory);
        leader.setLeaderLevel(newLevel);
        leader.setUpdatedAt(now);
        leader.setUpdatedBy(actor);
    }

    private TrainingCourse requireCourse(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            throw commonValidationHelper.validationError("courseCode là bắt buộc");
        }
        return trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện: " + courseCode));
    }

    private Leader requireLeader(UUID leaderId) {
        if (leaderId == null) {
            throw commonValidationHelper.validationError("leaderId là bắt buộc");
        }
        return leaderRepository.findByIdWithParish(leaderId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy leader: " + leaderId));
    }

    private void requireLeaderInCourseScope(Leader leader, TrainingCourse course) {
        if (leader == null || leader.getParish() == null || leader.getParish().getDeanery() == null || course == null) {
            throw commonValidationHelper.validationError("Leader và khóa huấn luyện phải có scope hợp lệ");
        }
        boolean sameScope = trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())
                ? course.getDeanery() != null && course.getDeanery().getId().equals(leader.getParish().getDeanery().getId())
                : course.getDiocese() != null && course.getDiocese().getId().equals(leader.getParish().getDeanery().getDiocese().getId());
        if (!sameScope) {
            throw commonValidationHelper.validationError("Leader phải thuộc cùng scope quản lý với khóa huấn luyện");
        }
    }

    private void requireUpdateAuthorityForTargetCourse(TrainingCourse course, Authentication authentication) {
        if (course == null) {
            throw commonValidationHelper.validationError("Khóa huấn luyện phải có scope sở hữu hợp lệ");
        }

        if (trainingWorkflowSupport.isDeaneryManaged(course.getCourseType())) {
            String authority = "training.certificate.update.deanery";
            if (!hasAuthority(authentication, authority)) {
                throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền cập nhật chứng chỉ cấp giáo hạt");
            }
            UUID activeDeaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, authority);
            if (course.getDeanery() == null || course.getDeanery().getId() == null
                    || !activeDeaneryId.equals(course.getDeanery().getId())) {
                throw commonValidationHelper.forbidden("Không được cập nhật chứng chỉ sang khóa huấn luyện ngoài giáo hạt quản lý");
            }
            return;
        }

        String authority = "training.certificate.update.diocese";
        if (!hasAuthority(authentication, authority)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền cập nhật chứng chỉ cấp giáo phận");
        }
        UUID activeDioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, authority);
        if (course.getDiocese() == null || course.getDiocese().getId() == null
                || !activeDioceseId.equals(course.getDiocese().getId())) {
            throw commonValidationHelper.forbidden("Không được cập nhật chứng chỉ sang khóa huấn luyện ngoài giáo phận quản lý");
        }
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private CertificateResponse toResponse(Certificate certificate) {
        CourseParticipation participation = null;
        if (certificate.getLeader() != null && certificate.getCourse() != null) {
            participation = courseParticipationRepository.findByLeaderIdAndCourseCode(
                    certificate.getLeader().getId(),
                    certificate.getCourse().getCourseCode()
            ).orElse(null);
        }
        boolean passed = participation != null
                && participation.getTotalScore() != null
                && participation.getCourse() != null
                && participation.getTotalScore().compareTo(participation.getCourse().getPassingScore()) >= 0;
        return CertificateResponse.builder()
                .certificateCode(certificate.getCertificateCode())
                .oldCertificateCode(certificate.getOldCertificateCode())
                .certificateName(certificate.getCertificateName())
                .leaderId(certificate.getLeader() == null ? null : certificate.getLeader().getId())
                .leaderFullName(buildFullName(certificate.getLeader()))
                .courseCode(certificate.getCourse() == null ? null : certificate.getCourse().getCourseCode())
                .courseName(certificate.getCourse() == null ? null : certificate.getCourse().getCourseName())
                .totalScore(participation == null ? null : participation.getTotalScore())
                .passingScore(participation == null || participation.getCourse() == null ? null : participation.getCourse().getPassingScore())
                .passed(participation == null || participation.getTotalScore() == null ? null : passed)
                .approvalReasonRequired(participation != null && participation.getTotalScore() != null && !passed)
                .result(certificate.getResult())
                .signedBy(certificate.getSignedBy())
                .issueDate(certificate.getIssueDate())
                .imageUrl(certificate.getImageUrl())
                .approvalStatus(certificate.getApprovalStatus())
                .systemSuggested(certificate.getSystemSuggested())
                .approvedByException(certificate.getApprovedByException())
                .exceptionApprovalReason(certificate.getExceptionApprovalReason())
                .approvedAt(certificate.getApprovedAt())
                .approvedBy(certificate.getApprovedBy())
                .status(certificate.getStatus())
                .build();
    }

    private String buildFullName(Leader leader) {
        if (leader == null) {
            return null;
        }
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
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
