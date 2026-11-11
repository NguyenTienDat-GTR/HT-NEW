package phucuong.ht.modules.training.courseType.service;

import phucuong.ht.config.EvictCaches;
import phucuong.ht.common.security.AuthorityChecks;
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
import phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard;
import phucuong.ht.modules.training.courseType.security.CourseTypeOwnership;
import phucuong.ht.modules.training.courseType.security.TrainingCourseTypeAccessEvaluator;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeCreateRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.modules.training.courseType.model.response.PrerequisiteOptionResponse;
import phucuong.ht.modules.training.courseType.model.response.TrainingCourseTypeResponse;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingCourseTypeService {

    private final TrainingCourseTypeRepository trainingCourseTypeRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final LeaderLevelService leaderLevelService;
    private final CourseTypeDioceseGuard dioceseGuard;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    private final CourseTypeOwnership ownership;

    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseTypeResponse create(TrainingCourseTypeCreateRequest request, Authentication authentication) {
        requireManagementUnitType(request.getManagementUnitType());
        UUID dioceseId = dioceseGuard.requireActorDioceseId(authentication);
        CourseTypeOwnership.Owner owner = ownership.ownerForNewType(request.getManagementUnitType(), authentication);
        String typeCode = generateUniqueTypeCode();

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo loại khóa huấn luyện");
        OffsetDateTime now = OffsetDateTime.now();

        TrainingCourseType courseType = new TrainingCourseType();
        courseType.setTypeCode(typeCode);
        courseType.setDioceseId(dioceseId);
        courseType.setOwnerUnitType(owner.unit());
        courseType.setOwnerDeaneryId(owner.deaneryId());
        courseType.setTypeName(normalizeText(request.getTypeName()));
        courseType.setManagementUnitType(request.getManagementUnitType());
        courseType.setRequiredCurrentLevel(leaderLevelService.requireExistingOrNull(request.getRequiredCurrentLevel(), "Cấp yêu cầu", dioceseId));
        courseType.setRequiredMinAge(normalizeMinAge(request.getRequiredMinAge()));
        courseType.setMaxYearsAfterPrerequisite(request.getMaxYearsAfterPrerequisite());
        courseType.setResultingLevel(leaderLevelService.requireExistingOrNull(request.getResultingLevel(), "Cấp độ đạt được", dioceseId));
        courseType.setHasCertificate(Boolean.TRUE.equals(request.getHasCertificate()));
        courseType.setCertificateShortCode(normalizeText(request.getCertificateShortCode()));
        requireShortCodeForCertified(courseType);
        courseType.setPrerequisiteType(resolvePrerequisite(typeCode, request.getPrerequisiteTypeCode(), dioceseId, request.getManagementUnitType(), owner.deaneryId(), null));
        courseType.setStatus(Boolean.TRUE);
        courseType.setCreatedAt(now);
        courseType.setUpdatedAt(now);
        courseType.setCreatedBy(actor);
        courseType.setUpdatedBy(actor);
        trainingCourseTypeRepository.save(courseType);
        return toResponse(courseType, ownership.scope(authentication));
    }

    @Transactional(readOnly = true)
    public Page<TrainingCourseTypeResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scoped = scopeByAccount(request, authentication);
        Page<TrainingCourseType> page = trainingCourseTypeRepository.findAll(scoped);
        CourseTypeOwnership.Scope scope = ownership.scope(authentication);
        List<TrainingCourseTypeResponse> content = page.getContent().stream()
                .map(type -> toResponse(type, scope))
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    // Tài khoản chỉ thấy loại khóa thuộc cấp quản lý của mình (giáo hạt hoặc giáo phận).
    private BaseSearchRequest scopeByAccount(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        boolean diocese = AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.READ_DIOCESE);
        boolean deanery = AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.READ_DEANERY);
        java.util.Optional<UUID> actorDiocese = dioceseGuard.actorDioceseId(authentication);
        if (diocese == deanery && actorDiocese.isEmpty()) {
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
        if (diocese != deanery) {
            copy.getFilters().put("managementUnitType", deanery ? UnitType.DEANERY : UnitType.DIOCESE);
        }
        // Danh mục loại khóa đi riêng theo giáo phận: chỉ thấy loại khóa của giáo phận mình.
        actorDiocese.ifPresent(id -> copy.getFilters().put("dioceseId", id));
        // Loại khóa giáo hạt tự tạo chỉ giáo hạt đó thấy; giáo phận chỉ thấy loại khóa của giáo phận (kể cả loại cấp giáo hạt dùng chung).
        return ownership.scopeList(copy, authentication);
    }


    public TrainingCourseTypeResponse findById(String typeCode, Authentication authentication) {
        TrainingCourseType courseType = requireType(typeCode);
        dioceseGuard.requireSameDiocese(courseType, authentication);
        ownership.requireVisible(courseType, authentication);
        return toResponse(courseType, ownership.scope(authentication)).toBuilder()
                .createdByName(auditActorNameResolver.resolve(courseType.getCreatedBy()))
                .updatedByName(auditActorNameResolver.resolve(courseType.getUpdatedBy()))
                .build();
    }

    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseTypeResponse update(String typeCode, TrainingCourseTypeUpdateRequest request, Authentication authentication) {
        TrainingCourseType courseType = requireType(typeCode);
        // Giáo phận của người thao tác tra một lần, dùng cho cả kiểm tra cùng giáo phận lẫn đối chiếu danh mục cấp độ.
        UUID actorDiocese = actorDioceseId(authentication);
        dioceseGuard.requireInDiocese(courseType, actorDiocese);
        ownership.requireVisible(courseType, authentication);
        ownership.requireManageable(courseType, authentication);

        if (request.getManagementUnitType() != null && request.getManagementUnitType() != courseType.getManagementUnitType()) {
            requireManagementUnitType(request.getManagementUnitType());
            if (CourseTypeOwnership.ownedByDeanery(courseType)) {
                throw commonValidationHelper.conflict("Loại khóa do giáo hạt tự tạo không đổi được cấp quản lý");
            }
            if (trainingCourseRepository.existsByCourseTypeCode(typeCode)) {
                throw commonValidationHelper.conflict("Không thể đổi cấp quản lý khi đã có khóa huấn luyện dùng loại này");
            }
            // Loại khóa cấp giáo hạt chỉ nhận khóa tiên quyết cấp giáo hạt: không đưa loại khóa này lên cấp giáo phận khi còn loại giáo hạt dựa vào nó.
            if (request.getManagementUnitType() == UnitType.DIOCESE && trainingCourseTypeRepository.existsDeaneryTypeWithPrerequisite(typeCode)) {
                throw commonValidationHelper.conflict("Không thể đổi sang cấp giáo phận vì đang có loại khóa cấp giáo hạt lấy loại khóa này làm khóa tiên quyết");
            }
            courseType.setManagementUnitType(request.getManagementUnitType());
        }
        if (request.getTypeName() != null) {
            courseType.setTypeName(normalizeText(request.getTypeName()));
        }
        if (request.getRequiredCurrentLevel() != null) {
            courseType.setRequiredCurrentLevel(leaderLevelService.requireExistingOrNull(request.getRequiredCurrentLevel(), "Cấp yêu cầu", actorDiocese));
        }
        if (request.getRequiredMinAge() != null) {
            courseType.setRequiredMinAge(normalizeMinAge(request.getRequiredMinAge()));
        }
        if (Boolean.TRUE.equals(request.getClearMaxYearsAfterPrerequisite())) {
            courseType.setMaxYearsAfterPrerequisite(null);
        } else if (request.getMaxYearsAfterPrerequisite() != null) {
            courseType.setMaxYearsAfterPrerequisite(request.getMaxYearsAfterPrerequisite());
        }
        // Bỏ trống cấp độ khi hoàn thành: từ đó hoàn thành khóa thuộc loại này không thăng cấp nữa.
        if (Boolean.TRUE.equals(request.getClearResultingLevel())) {
            courseType.setResultingLevel(null);
        } else if (request.getResultingLevel() != null) {
            courseType.setResultingLevel(leaderLevelService.requireExistingOrNull(request.getResultingLevel(), "Cấp độ đạt được", actorDiocese));
        }
        if (request.getHasCertificate() != null) {
            courseType.setHasCertificate(request.getHasCertificate());
        }
        if (request.getCertificateShortCode() != null) {
            courseType.setCertificateShortCode(normalizeText(request.getCertificateShortCode()));
        }
        if (Boolean.TRUE.equals(request.getClearPrerequisite())) {
            courseType.setPrerequisiteType(null);
        } else if (request.getPrerequisiteTypeCode() != null && !request.getPrerequisiteTypeCode().isBlank()) {
            courseType.setPrerequisiteType(resolvePrerequisite(typeCode, request.getPrerequisiteTypeCode(), courseType.getDioceseId(),
                    courseType.getManagementUnitType(), courseType.getOwnerDeaneryId(), courseType.getPrerequisiteType()));
        }
        requirePrerequisiteLevel(courseType.getManagementUnitType(), courseType.getOwnerDeaneryId(), courseType.getPrerequisiteType());

        requireShortCodeForCertified(courseType);
        courseType.setUpdatedAt(OffsetDateTime.now());
        courseType.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật loại khóa huấn luyện"));
        return toResponse(courseType, ownership.scope(authentication));
    }

    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseTypeResponse toggleStatus(String typeCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái loại khóa huấn luyện");
        TrainingCourseType target = requireType(typeCode);
        dioceseGuard.requireSameDiocese(target, authentication);
        ownership.requireManageable(target, authentication);
        trainingCourseTypeRepository.toggleStatus(typeCode, actor);
        return findById(typeCode, authentication);
    }

    // Loại khóa có cấp chứng nhận bắt buộc có mã viết tắt để sinh mã chứng chỉ.
    private void requireShortCodeForCertified(TrainingCourseType courseType) {
        if (courseType.isHasCertificate() && (courseType.getCertificateShortCode() == null || courseType.getCertificateShortCode().isBlank())) {
            throw commonValidationHelper.validationError("Phải nhập mã viết tắt chứng chỉ cho loại khóa có cấp chứng nhận");
        }
    }

    // Tuổi tối thiểu: 0 hoặc bỏ trống = không yêu cầu (lưu thành trống); số âm không hợp lệ.
    private Integer normalizeMinAge(Integer requiredMinAge) {
        if (requiredMinAge == null || requiredMinAge == 0) {
            return null;
        }
        if (requiredMinAge < 0) {
            throw commonValidationHelper.validationError("Tuổi tối thiểu không được nhỏ hơn 0");
        }
        return requiredMinAge;
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

    // current: khóa tiên quyết đang lưu của loại khóa (khi sửa) - giữ nguyên được dù đã ngừng hoạt động.
    private TrainingCourseType resolvePrerequisite(String typeCode, String prerequisiteTypeCode, UUID dioceseId,
                                                   UnitType ownerLevel, UUID ownerDeaneryId, TrainingCourseType current) {
        if (prerequisiteTypeCode == null || prerequisiteTypeCode.isBlank()) {
            return null;
        }
        String normalized = normalizeCode(prerequisiteTypeCode);
        if (normalized.equals(typeCode)) {
            throw commonValidationHelper.validationError("Loại khóa không thể là khóa tiên quyết của chính nó");
        }
        TrainingCourseType prerequisite = trainingCourseTypeRepository.findByCodeWithPrerequisite(normalized)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy loại khóa tiên quyết: " + normalized));
        if (dioceseId != null && !dioceseId.equals(prerequisite.getDioceseId())) {
            throw commonValidationHelper.validationError("Khóa tiên quyết phải thuộc cùng giáo phận");
        }
        boolean unchanged = current != null && normalized.equals(current.getTypeCode());
        if (!unchanged && !Boolean.TRUE.equals(prerequisite.getStatus())) {
            throw commonValidationHelper.validationError("Loại khóa tiên quyết đã ngừng hoạt động: " + prerequisite.getTypeName());
        }
        requirePrerequisiteLevel(ownerLevel, ownerDeaneryId, prerequisite);
        if (typeCode != null && prerequisiteChainContains(prerequisite, typeCode)) {
            throw commonValidationHelper.validationError("Không thể chọn " + prerequisite.getTypeName()
                    + " vì loại khóa này đang cần loại khóa bạn đang sửa làm khóa tiên quyết (tạo vòng lặp)");
        }
        return prerequisite;
    }

    // Khóa tiên quyết chọn được:
    //  - cấp giáo phận: loại khóa cấp giáo phận hoặc loại khóa cấp giáo hạt do giáo phận tạo (dùng chung);
    //  - cấp giáo hạt: chỉ loại khóa cấp giáo hạt, gồm loại do giáo phận tạo (dùng chung) và loại do chính giáo hạt đó tạo.
    // Loại khóa do giáo hạt khác tự tạo không bao giờ chọn được.
    private void requirePrerequisiteLevel(UnitType ownerLevel, UUID ownerDeaneryId, TrainingCourseType prerequisite) {
        if (prerequisite == null || allowedPrerequisite(ownerLevel, ownerDeaneryId, prerequisite)) {
            return;
        }
        if (prerequisite.getManagementUnitType() == UnitType.DIOCESE) {
            throw commonValidationHelper.validationError("Loại khóa cấp giáo hạt chỉ được chọn khóa tiên quyết cấp giáo hạt");
        }
        throw commonValidationHelper.validationError("Chỉ chọn được loại khóa cấp giáo hạt do giáo phận tạo (dùng chung) hoặc do chính giáo hạt này tạo");
    }

    private static boolean allowedPrerequisite(UnitType ownerLevel, UUID ownerDeaneryId, TrainingCourseType candidate) {
        if (candidate.getManagementUnitType() == UnitType.DIOCESE) {
            return ownerLevel != UnitType.DEANERY;
        }
        return candidate.getOwnerUnitType() != UnitType.DEANERY || Objects.equals(candidate.getOwnerDeaneryId(), ownerDeaneryId);
    }

    // Đi theo chuỗi khóa tiên quyết bắt đầu từ start xem có gặp typeCode không (chặn vòng lặp A cần B, B cần A).
    private boolean prerequisiteChainContains(TrainingCourseType start, String typeCode) {
        Set<String> visited = new HashSet<>();
        TrainingCourseType cursor = start;
        while (cursor != null && visited.add(cursor.getTypeCode())) {
            if (typeCode.equals(cursor.getTypeCode())) {
                return true;
            }
            TrainingCourseType next = cursor.getPrerequisiteType();
            cursor = next == null ? null : trainingCourseTypeRepository.findByCodeWithPrerequisite(next.getTypeCode()).orElse(null);
        }
        return false;
    }

    /**
     * Loại khóa chọn được làm khóa tiên quyết cho loại khóa cấp managementUnitType (đang tạo) hoặc loại khóa forTypeCode (đang sửa).
     * Không theo quyền xem danh mục theo cấp: tài khoản giáo phận vẫn thấy loại khóa cấp giáo hạt ở đây.
     */
    @Transactional(readOnly = true)
    public List<PrerequisiteOptionResponse> prerequisiteOptions(UnitType managementUnitType, String forTypeCode, Authentication authentication) {
        TrainingCourseType editing = forTypeCode == null || forTypeCode.isBlank() ? null : requireType(forTypeCode.trim());
        UUID dioceseId;
        if (editing != null) {
            dioceseGuard.requireSameDiocese(editing, authentication);
            ownership.requireVisible(editing, authentication);
            dioceseId = editing.getDioceseId();
        } else {
            dioceseId = dioceseGuard.requireActorDioceseId(authentication);
        }
        UnitType ownerLevel = managementUnitType != null ? managementUnitType
                : editing == null ? UnitType.DIOCESE : editing.getManagementUnitType();
        requireManagementUnitType(ownerLevel);
        UUID ownerDeaneryId = editing != null ? editing.getOwnerDeaneryId()
                : ownerLevel == UnitType.DEANERY ? ownership.ownerForNewType(UnitType.DEANERY, authentication).deaneryId() : null;
        String currentCode = editing == null || editing.getPrerequisiteType() == null ? null : editing.getPrerequisiteType().getTypeCode();

        List<TrainingCourseType> types = trainingCourseTypeRepository.findAllInDioceseWithPrerequisite(dioceseId);
        Map<String, TrainingCourseType> byCode = types.stream().collect(Collectors.toMap(TrainingCourseType::getTypeCode, Function.identity()));
        Map<String, Long> courseCounts = trainingCourseTypeRepository.countCoursesByType(dioceseId);
        Map<String, Long> leaderCounts = trainingCourseTypeRepository.countCompletedLeadersByType(dioceseId);
        var levelOrder = leaderLevelService.order(dioceseId);

        return types.stream()
                .filter(type -> editing == null || !type.getTypeCode().equals(editing.getTypeCode()))
                .filter(type -> allowedPrerequisite(ownerLevel, ownerDeaneryId, type))
                // Loại khóa đã ngừng hoạt động không chọn mới được, nhưng vẫn hiện nếu đang là khóa tiên quyết hiện tại.
                .filter(type -> Boolean.TRUE.equals(type.getStatus()) || type.getTypeCode().equals(currentCode))
                .map(type -> {
                    boolean cycle = editing != null && chainContains(type, editing.getTypeCode(), byCode);
                    return PrerequisiteOptionResponse.builder()
                            .typeCode(type.getTypeCode())
                            .typeName(type.getTypeName())
                            .managementUnitType(type.getManagementUnitType())
                            .ownerUnitType(type.getOwnerUnitType())
                            .status(type.getStatus())
                            .requiredCurrentLevel(type.getRequiredCurrentLevel())
                            .requiredCurrentLevelName(type.getRequiredCurrentLevel() == null ? null : levelOrder.nameOf(type.getRequiredCurrentLevel()))
                            .resultingLevel(type.getResultingLevel())
                            .resultingLevelName(type.getResultingLevel() == null || "NONE".equals(type.getResultingLevel()) ? null : levelOrder.nameOf(type.getResultingLevel()))
                            .prerequisiteTypeName(type.getPrerequisiteType() == null ? null : type.getPrerequisiteType().getTypeName())
                            .courseCount(courseCounts.getOrDefault(type.getTypeCode(), 0L))
                            .completedLeaderCount(leaderCounts.getOrDefault(type.getTypeCode(), 0L))
                            .current(type.getTypeCode().equals(currentCode))
                            .selectable(!cycle)
                            .disabledReason(cycle ? "Loại khóa này đang cần loại khóa bạn đang sửa làm khóa tiên quyết" : null)
                            .build();
                })
                .toList();
    }

    // Như prerequisiteChainContains nhưng dùng danh sách đã tải sẵn (không truy vấn thêm).
    private static boolean chainContains(TrainingCourseType start, String typeCode, Map<String, TrainingCourseType> byCode) {
        Set<String> visited = new HashSet<>();
        String cursor = start.getPrerequisiteType() == null ? null : start.getPrerequisiteType().getTypeCode();
        while (cursor != null && visited.add(cursor)) {
            if (Objects.equals(cursor, typeCode)) {
                return true;
            }
            TrainingCourseType next = byCode.get(cursor);
            cursor = next == null || next.getPrerequisiteType() == null ? null : next.getPrerequisiteType().getTypeCode();
        }
        return false;
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

    private TrainingCourseTypeResponse toResponse(TrainingCourseType courseType, CourseTypeOwnership.Scope scope) {
        return toResponse(courseType).toBuilder().editable(ownership.canManage(courseType, scope)).build();
    }

    private TrainingCourseTypeResponse toResponse(TrainingCourseType courseType) {
        return TrainingCourseTypeResponse.builder()
                .typeCode(courseType.getTypeCode())
                .typeName(courseType.getTypeName())
                .dioceseId(courseType.getDioceseId())
                .managementUnitType(courseType.getManagementUnitType())
                .ownerUnitType(courseType.getOwnerUnitType())
                .ownerDeaneryId(courseType.getOwnerDeaneryId())
                .requiredCurrentLevel(courseType.getRequiredCurrentLevel())
                .requiredMinAge(courseType.getRequiredMinAge())
                .maxYearsAfterPrerequisite(courseType.getMaxYearsAfterPrerequisite())
                .prerequisiteTypeCode(courseType.getPrerequisiteType() == null ? null : courseType.getPrerequisiteType().getTypeCode())
                .prerequisiteTypeName(courseType.getPrerequisiteType() == null ? null : courseType.getPrerequisiteType().getTypeName())
                .prerequisiteManagementUnitType(courseType.getPrerequisiteType() == null ? null : courseType.getPrerequisiteType().getManagementUnitType())
                .prerequisiteOwnerDeaneryId(courseType.getPrerequisiteType() == null ? null : courseType.getPrerequisiteType().getOwnerDeaneryId())
                .resultingLevel(courseType.getResultingLevel())
                .hasCertificate(courseType.isHasCertificate())
                .certificateShortCode(courseType.getCertificateShortCode())
                .status(courseType.getStatus())
                .createdBy(courseType.getCreatedBy())
                .updatedBy(courseType.getUpdatedBy())
                .createdAt(courseType.getCreatedAt())
                .updatedAt(courseType.getUpdatedAt())
                .build();
    }
}
