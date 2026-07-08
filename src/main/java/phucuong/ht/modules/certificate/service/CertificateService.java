package phucuong.ht.modules.certificate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
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
    public CertificateResponse create(CertificateCreateRequest request, Authentication authentication) {
        TrainingCourse course = requireCourse(request.getCourseCode());
        Leader leader = requireLeader(request.getLeaderId());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Cannot determine certificate creator");

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
            throw commonValidationHelper.forbidden("Current account cannot read certificates");
        }
        Page<Certificate> page = certificateRepository.findAll(scopedRequest);
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    @Cacheable(cacheNames = CacheNames.CERTIFICATE_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #certificateCode)")
    public CertificateResponse findById(String certificateCode, Authentication authentication) {
        return toResponse(certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Certificate not found: " + certificateCode)));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    public CertificateResponse update(String certificateCode, CertificateUpdateRequest request, Authentication authentication) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Certificate not found: " + certificateCode));
        if (request.getOldCertificateCode() != null) {
            certificate.setOldCertificateCode(request.getOldCertificateCode());
        }
        if (request.getCertificateName() != null) {
            certificate.setCertificateName(request.getCertificateName());
        }
        if (request.getLeaderId() != null) {
            certificate.setLeader(requireLeader(request.getLeaderId()));
        }
        if (request.getCourseCode() != null) {
            certificate.setCourse(requireCourse(request.getCourseCode()));
        }
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
        certificate.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Cannot determine certificate updater"));
        return toResponse(certificate);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    public void delete(String certificateCode) {
        certificateRepository.delete(certificateCode);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    public CertificateResponse toggleStatus(String certificateCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        certificateRepository.toggleStatus(certificateCode, commonValidationHelper.requireAuditActor(authentication, "Cannot determine certificate actor"));
        return findById(certificateCode, authentication);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    public CertificateResponse approve(String certificateCode, CertificateApproveRequest request, Authentication authentication) {
        Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(certificateCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Certificate not found: " + certificateCode));
        if (certificate.getLeader() == null || certificate.getCourse() == null) {
            throw commonValidationHelper.validationError("Certificate must be linked to a leader and training course");
        }
        if (certificate.getApprovalStatus() == WorkflowApprovalStatus.APPROVED) {
            throw commonValidationHelper.conflict("Certificate has already been approved");
        }

        CourseParticipation participation = courseParticipationRepository.findByLeaderIdAndCourseCode(
                        certificate.getLeader().getId(),
                        certificate.getCourse().getCourseCode()
                )
                .orElseThrow(() -> commonValidationHelper.validationError("Leader does not have a participation record for this training course"));

        boolean passingScore = participation.getCourse() != null
                && participation.getCourse().getScoreFormula() != null
                && participation.getTotalScore() != null
                && participation.getTotalScore().compareTo(participation.getCourse().getScoreFormula().getPassingScore()) >= 0;
        String approvalReason = request == null ? null : commonValidationHelper.normalizeOptionalText(request.getApprovalReason());
        if (!passingScore && approvalReason == null) {
            throw commonValidationHelper.validationError("approvalReason is required when approving a certificate for a leader who has not reached the passing score");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Cannot determine certificate approver");
        certificate.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        certificate.setApprovedAt(OffsetDateTime.now());
        certificate.setApprovedBy(actor);
        certificate.setApprovedByException(!passingScore);
        certificate.setExceptionApprovalReason(approvalReason);
        if (certificate.getIssueDate() == null) {
            certificate.setIssueDate(LocalDate.now());
        }
        certificate.setUpdatedBy(actor);

        participation.setCertificateApprovalStatus(WorkflowApprovalStatus.APPROVED);
        participation.setCertificateApprovedAt(OffsetDateTime.now());
        participation.setApprovedBy(actor);
        participation.setUpdatedBy(actor);

        trainingWorkflowSupport.certificateLevelFor(certificate.getCourse().getCourseType())
                .ifPresent(newLevel -> promoteLeader(certificate.getLeader(), newLevel, actor, certificateCode));

        publishEvent("training.certificate.approved", "certificate", certificateCode, actor, Map.of(
                "courseCode", certificate.getCourse().getCourseCode(),
                "approvedByException", !passingScore
        ));
        return toResponse(certificate);
    }

    private void promoteLeader(Leader leader, LeaderLevel newLevel, String actor, String certificateCode) {
        if (leader.getLeaderLevel() == newLevel) {
            return;
        }

        RankHistory rankHistory = new RankHistory();
        rankHistory.setLeader(leader);
        rankHistory.setOldLevel(leader.getLeaderLevel());
        rankHistory.setNewLevel(newLevel);
        rankHistory.setPromotionDate(LocalDate.now());
        rankHistory.setNote("Approved from certificate " + certificateCode);
        rankHistory.setStatus(Boolean.TRUE);
        rankHistory.setCreatedBy(actor);
        rankHistory.setUpdatedBy(actor);
        rankHistoryRepository.save(rankHistory);
        leader.setLeaderLevel(newLevel);
        leader.setUpdatedBy(actor);
    }

    private TrainingCourse requireCourse(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            throw commonValidationHelper.validationError("courseCode is required");
        }
        return trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Training course not found: " + courseCode));
    }

    private Leader requireLeader(UUID leaderId) {
        if (leaderId == null) {
            throw commonValidationHelper.validationError("leaderId is required");
        }
        return leaderRepository.findByIdWithParish(leaderId)
                .orElseThrow(() -> commonValidationHelper.notFound("Leader not found: " + leaderId));
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
        return CertificateResponse.builder()
                .certificateCode(certificate.getCertificateCode())
                .oldCertificateCode(certificate.getOldCertificateCode())
                .certificateName(certificate.getCertificateName())
                .leaderId(certificate.getLeader() == null ? null : certificate.getLeader().getId())
                .courseCode(certificate.getCourse() == null ? null : certificate.getCourse().getCourseCode())
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
