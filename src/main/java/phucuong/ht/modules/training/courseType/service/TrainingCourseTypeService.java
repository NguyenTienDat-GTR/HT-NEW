package phucuong.ht.modules.training.courseType.service;

import lombok.RequiredArgsConstructor;
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
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeCreateRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.modules.training.courseType.model.response.TrainingCourseTypeResponse;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingCourseTypeService {

    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CommonValidationHelper commonValidationHelper;

    @Transactional
    public TrainingCourseTypeResponse create(TrainingCourseTypeCreateRequest request, Authentication authentication) {
        requireManagementUnitType(request.getManagementUnitType());
        String typeCode = normalizeCode(request.getTypeCode());
        if (trainingCourseTypeRepository.existsByTypeCode(typeCode)) {
            throw commonValidationHelper.conflict("Mã loại khóa huấn luyện đã tồn tại: " + typeCode);
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo loại khóa huấn luyện");
        OffsetDateTime now = OffsetDateTime.now();

        TrainingCourseType courseType = new TrainingCourseType();
        courseType.setTypeCode(typeCode);
        courseType.setTypeName(normalizeText(request.getTypeName()));
        courseType.setManagementUnitType(request.getManagementUnitType());
        courseType.setRequiredCurrentLevel(request.getRequiredCurrentLevel());
        courseType.setRequiredMinAge(request.getRequiredMinAge());
        courseType.setRequiredYearsActive(request.getRequiredYearsActive());
        courseType.setResultingLevel(request.getResultingLevel());
        courseType.setCertificateShortCode(normalizeText(request.getCertificateShortCode()));
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
        Page<TrainingCourseType> page = trainingCourseTypeRepository.findAll(request);
        List<TrainingCourseTypeResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    public TrainingCourseTypeResponse findById(String typeCode, Authentication authentication) {
        return toResponse(requireType(typeCode));
    }

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
            courseType.setRequiredCurrentLevel(request.getRequiredCurrentLevel());
        }
        if (request.getRequiredMinAge() != null) {
            courseType.setRequiredMinAge(request.getRequiredMinAge());
        }
        if (request.getRequiredYearsActive() != null) {
            courseType.setRequiredYearsActive(request.getRequiredYearsActive());
        }
        if (request.getResultingLevel() != null) {
            courseType.setResultingLevel(request.getResultingLevel());
        }
        if (request.getCertificateShortCode() != null) {
            courseType.setCertificateShortCode(normalizeText(request.getCertificateShortCode()));
        }
        if (Boolean.TRUE.equals(request.getClearPrerequisite())) {
            courseType.setPrerequisiteType(null);
        } else if (request.getPrerequisiteTypeCode() != null && !request.getPrerequisiteTypeCode().isBlank()) {
            courseType.setPrerequisiteType(resolvePrerequisite(typeCode, request.getPrerequisiteTypeCode()));
        }

        courseType.setUpdatedAt(OffsetDateTime.now());
        courseType.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật loại khóa huấn luyện"));
        return toResponse(courseType);
    }

    @Transactional
    public TrainingCourseTypeResponse toggleStatus(String typeCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái loại khóa huấn luyện");
        trainingCourseTypeRepository.toggleStatus(typeCode, actor);
        return findById(typeCode, authentication);
    }

    private void requireManagementUnitType(UnitType managementUnitType) {
        if (managementUnitType == null) {
            throw commonValidationHelper.validationError("Cấp quản lý là bắt buộc");
        }
        if (managementUnitType != UnitType.DIOCESE && managementUnitType != UnitType.DEANERY) {
            throw commonValidationHelper.validationError("Cấp quản lý loại khóa chỉ được là DIOCESE hoặc DEANERY");
        }
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
