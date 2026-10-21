package phucuong.ht.modules.training.courseType.service;

import phucuong.ht.config.EvictCaches;
import lombok.RequiredArgsConstructor;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.security.TrainingCourseTypeAccessEvaluator;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeCreateRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.modules.training.courseType.model.response.TrainingCourseTypeResponse;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingCourseTypeService {

    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final LeaderLevelService leaderLevelService;

    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseTypeResponse create(TrainingCourseTypeCreateRequest request, Authentication authentication) {
        requireManagementUnitType(request.getManagementUnitType());
        String typeCode = generateUniqueTypeCode();

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo loại khóa huấn luyện");
        OffsetDateTime now = OffsetDateTime.now();

        TrainingCourseType courseType = new TrainingCourseType();
        courseType.setTypeCode(typeCode);
        courseType.setTypeName(normalizeText(request.getTypeName()));
        courseType.setManagementUnitType(request.getManagementUnitType());
        courseType.setRequiredCurrentLevel(leaderLevelService.requireExistingOrNull(request.getRequiredCurrentLevel(), "Cấp yêu cầu", actorDioceseId(authentication)));
        courseType.setRequiredMinAge(request.getRequiredMinAge());
        courseType.setRequiredYearsActive(request.getRequiredYearsActive());
        courseType.setMaxYearsAfterPrerequisite(request.getMaxYearsAfterPrerequisite());
        courseType.setResultingLevel(leaderLevelService.requireExistingOrNull(request.getResultingLevel(), "Cấp độ đạt được", actorDioceseId(authentication)));
        courseType.setCertificateShortCode(normalizeText(request.getCertificateShortCode()));
        requireShortCodeForCertified(courseType);
        courseType.setPrerequisiteType(resolvePrerequisite(typeCode, request.getPrerequisiteTypeCode()));
        courseType.setStatus(Boolean.TRUE);
        courseType.setCreatedAt(now);
        courseType.setUpdatedAt(now);
        courseType.setCreatedBy(actor);
        courseType.setUpdatedBy(actor);
        trainingCourseTypeRepository.save(courseType);
        return toResponse(courseType);
    }

    @Transactional(readOnly = true)
    public Page<TrainingCourseTypeResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scoped = scopeByAccount(request, authentication);
        Page<TrainingCourseType> page = trainingCourseTypeRepository.findAll(scoped);
        List<TrainingCourseTypeResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    // Tài khoản chỉ thấy loại khóa thuộc cấp quản lý của mình (giáo hạt hoặc giáo phận).
    private BaseSearchRequest scopeByAccount(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        boolean diocese = hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.READ_DIOCESE);
        boolean deanery = hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.READ_DEANERY);
        if (diocese == deanery) {
            return source;
        }
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        copy.getFilters().put("managementUnitType", deanery ? UnitType.DEANERY : UnitType.DIOCESE);
        return copy;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    public TrainingCourseTypeResponse findById(String typeCode, Authentication authentication) {
        return toResponse(requireType(typeCode));
    }

    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseTypeResponse update(String typeCode, TrainingCourseTypeUpdateRequest request, Authentication authentication) {
        TrainingCourseType courseType = requireType(typeCode);

        if (request.getManagementUnitType() != null && request.getManagementUnitType() != courseType.getManagementUnitType()) {
            requireManagementUnitType(request.getManagementUnitType());
            if (trainingCourseRepository.existsByCourseTypeCode(typeCode)) {
                throw commonValidationHelper.conflict("Không thể đổi cấp quản lý khi đã có khóa huấn luyện dùng loại này");
            }
            courseType.setManagementUnitType(request.getManagementUnitType());
        }
        if (request.getTypeName() != null) {
            courseType.setTypeName(normalizeText(request.getTypeName()));
        }
        if (request.getRequiredCurrentLevel() != null) {
            courseType.setRequiredCurrentLevel(leaderLevelService.requireExistingOrNull(request.getRequiredCurrentLevel(), "Cấp yêu cầu", actorDioceseId(authentication)));
        }
        if (request.getRequiredMinAge() != null) {
            courseType.setRequiredMinAge(request.getRequiredMinAge());
        }
        if (request.getRequiredYearsActive() != null) {
            courseType.setRequiredYearsActive(request.getRequiredYearsActive());
        }
        if (Boolean.TRUE.equals(request.getClearMaxYearsAfterPrerequisite())) {
            courseType.setMaxYearsAfterPrerequisite(null);
        } else if (request.getMaxYearsAfterPrerequisite() != null) {
            courseType.setMaxYearsAfterPrerequisite(request.getMaxYearsAfterPrerequisite());
        }
        if (request.getResultingLevel() != null) {
            courseType.setResultingLevel(leaderLevelService.requireExistingOrNull(request.getResultingLevel(), "Cấp độ đạt được", actorDioceseId(authentication)));
        }
        if (request.getCertificateShortCode() != null) {
            courseType.setCertificateShortCode(normalizeText(request.getCertificateShortCode()));
        }
        if (Boolean.TRUE.equals(request.getClearPrerequisite())) {
            courseType.setPrerequisiteType(null);
        } else if (request.getPrerequisiteTypeCode() != null && !request.getPrerequisiteTypeCode().isBlank()) {
            courseType.setPrerequisiteType(resolvePrerequisite(typeCode, request.getPrerequisiteTypeCode()));
        }

        requireShortCodeForCertified(courseType);
        courseType.setUpdatedAt(OffsetDateTime.now());
        courseType.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật loại khóa huấn luyện"));
        return toResponse(courseType);
    }

    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseTypeResponse toggleStatus(String typeCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái loại khóa huấn luyện");
        trainingCourseTypeRepository.toggleStatus(typeCode, actor);
        return findById(typeCode, authentication);
    }

    // Loại khóa có cấp độ đạt được (yêu cầu chứng nhận) bắt buộc có mã viết tắt để sinh mã chứng chỉ.
    private void requireShortCodeForCertified(TrainingCourseType courseType) {
        String level = courseType.getResultingLevel();
        boolean certified = level != null && !"NONE".equals(level);
        if (certified && (courseType.getCertificateShortCode() == null || courseType.getCertificateShortCode().isBlank())) {
            throw commonValidationHelper.validationError("Phải nhập mã viết tắt chứng chỉ cho loại khóa có cấp độ đạt được");
        }
    }

    // Giáo phận của người thao tác, để đối chiếu cấp độ với danh mục của giáo phận (null nếu không xác định).
    private java.util.UUID actorDioceseId(Authentication authentication) {
        return leaderLevelService.activeDioceseId(authentication).orElse(null);
    }

    private void requireManagementUnitType(UnitType managementUnitType) {
        if (managementUnitType == null) {
            throw commonValidationHelper.validationError("Cấp quản lý là bắt buộc");
        }
        if (managementUnitType != UnitType.DIOCESE && managementUnitType != UnitType.DEANERY) {
            throw commonValidationHelper.validationError("Cấp quản lý loại khóa chỉ được là DIOCESE hoặc DEANERY");
        }
    }

    private String generateUniqueTypeCode() {
        String candidate;
        do {
            candidate = UUID.randomUUID().toString();
        } while (trainingCourseTypeRepository.existsByTypeCode(candidate));
        return candidate;
    }

    private TrainingCourseType resolvePrerequisite(String typeCode, String prerequisiteTypeCode) {
        if (prerequisiteTypeCode == null || prerequisiteTypeCode.isBlank()) {
            return null;
        }
        String normalized = normalizeCode(prerequisiteTypeCode);
        if (normalized.equals(typeCode)) {
            throw commonValidationHelper.validationError("Loại khóa không thể là khóa tiên quyết của chính nó");
        }
        return trainingCourseTypeRepository.findByCodeWithPrerequisite(normalized)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy loại khóa tiên quyết: " + normalized));
    }

    private TrainingCourseType requireType(String typeCode) {
        return trainingCourseTypeRepository.findByCodeWithPrerequisite(typeCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy loại khóa huấn luyện: " + typeCode));
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TrainingCourseTypeResponse toResponse(TrainingCourseType courseType) {
        return TrainingCourseTypeResponse.builder()
                .typeCode(courseType.getTypeCode())
                .typeName(courseType.getTypeName())
                .managementUnitType(courseType.getManagementUnitType())
                .requiredCurrentLevel(courseType.getRequiredCurrentLevel())
                .requiredMinAge(courseType.getRequiredMinAge())
                .requiredYearsActive(courseType.getRequiredYearsActive())
                .maxYearsAfterPrerequisite(courseType.getMaxYearsAfterPrerequisite())
                .prerequisiteTypeCode(courseType.getPrerequisiteType() == null ? null : courseType.getPrerequisiteType().getTypeCode())
                .prerequisiteTypeName(courseType.getPrerequisiteType() == null ? null : courseType.getPrerequisiteType().getTypeName())
                .resultingLevel(courseType.getResultingLevel())
                .certificateShortCode(courseType.getCertificateShortCode())
                .status(courseType.getStatus())
                .createdBy(courseType.getCreatedBy())
                .updatedBy(courseType.getUpdatedBy())
                .createdAt(courseType.getCreatedAt())
                .updatedAt(courseType.getUpdatedAt())
                .build();
    }
}
