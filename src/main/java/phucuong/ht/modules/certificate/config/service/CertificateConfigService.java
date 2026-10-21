package phucuong.ht.modules.certificate.config.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.config.model.CertificateMergeField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfig;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfigAssignment;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigAssignRequest;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigAssignmentResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificateFieldCatalogItem;
import phucuong.ht.modules.certificate.config.model.response.CertifiedCourseTypeResponse;
import phucuong.ht.modules.certificate.config.repository.CertificateConfigAssignmentRepository;
import phucuong.ht.modules.certificate.config.repository.CertificateConfigRepository;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver.ActorScope;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateConfigService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, String> LEVEL_LABELS = Map.ofEntries(
            Map.entry("NONE", "Chưa có cấp"),
            Map.entry("HT_XU", "Huynh trưởng xứ"),
            Map.entry("DU_TRUONG", "Dự trưởng"),
            Map.entry("HT_I", "Huynh trưởng cấp I"),
            Map.entry("HT_II", "Huynh trưởng cấp II"),
            Map.entry("HT_III", "Huynh trưởng cấp III"),
            Map.entry("HLV_I", "Huấn luyện viên cấp I"),
            Map.entry("HLV_II", "Huấn luyện viên cấp II"),
            Map.entry("HLV_III", "Huấn luyện viên cấp III"));

    private final CertificateConfigRepository configRepository;
    private final CertificateConfigAssignmentRepository assignmentRepository;
    private final CertificateConfigScopeResolver scopeResolver;
    private final CertificateRepository certificateRepository;
    private final TrainingCourseTypeRepository courseTypeRepository;
    private final CertificatePdfRenderer pdfRenderer;
    private final CommonValidationHelper validationHelper;
    private final securityUtils securityUtils;

    public record PdfExport(byte[] content, String filename) {
    }

    // Danh sách trường cố định cho trình soạn bố cục.
    public List<CertificateFieldCatalogItem> fieldCatalog() {
        return Arrays.stream(CertificateMergeField.values())
                .map(f -> new CertificateFieldCatalogItem(f.getKey(), f.getLabel(), f.getGroup()))
                .toList();
    }

    // Loại khóa có yêu cầu chứng nhận, cho ô chọn phạm vi (độc lập với quyền đọc loại khóa).
    @Transactional(readOnly = true)
    public List<CertifiedCourseTypeResponse> certifiedCourseTypes() {
        return courseTypeRepository.findCertifiedTypes().stream()
                .map(type -> new CertifiedCourseTypeResponse(type.getTypeCode(), type.getTypeName()))
                .toList();
    }

    // Toàn bộ cấu hình người dùng xem/dùng được (phạm vi mình, cấp trên, hệ thống) ở dạng gọn (không kèm ảnh nền / bố cục),
    // cho ô chọn cấu hình và để xác định cấu hình chung.
    @Transactional(readOnly = true)
    public List<CertificateConfigResponse> options(Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        Map<UUID, List<CertifiedCourseTypeResponse>> usage = usageByConfig(actor);
        return visibleConfigs(actor).stream()
                .map(config -> toSummary(config, actor, usage.getOrDefault(config.getId(), List.of())))
                .toList();
    }

    // Chi tiết một cấu hình (kèm ảnh nền + bố cục) để chỉnh sửa hoặc xem trước.
    @Transactional(readOnly = true)
    public CertificateConfigResponse get(UUID id, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        CertificateConfig config = configRepository.findById(id)
                .filter(c -> actor.canSee(c.getScopeKey()))
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấu hình chứng nhận"));
        return toResponse(config, actor, usageByConfig(actor).getOrDefault(id, List.of()));
    }

    // Tìm kiếm / lọc / sắp xếp / phân trang thư viện cấu hình (dạng gọn, không kèm ảnh nền / bố cục).
    // search: tên cấu hình hoặc tên loại khóa đang dùng (không phân biệt hoa thường và dấu).
    // filters: scope (SYSTEM|DIOCESE|DEANERY), isDefault, used (có loại khóa đang dùng hay không),
    //          paperSize, orientation, courseTypeCode (cấu hình đang dùng cho loại khóa đó).
    // sortBy: name | updatedAt | usedByCount | scopeLevel; mặc định cấu hình chung lên đầu rồi theo tên.
    @Transactional(readOnly = true)
    public Page<CertificateConfigResponse> search(BaseSearchRequest request, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        Map<UUID, List<CertifiedCourseTypeResponse>> usage = usageByConfig(actor);
        Map<String, Object> filters = request.getFilters() == null ? Map.of() : request.getFilters();
        String keyword = foldText(request.getSearch());
        String scope = filterText(filters, "scope");
        Boolean isDefault = filterBoolean(filters, "isDefault");
        Boolean used = filterBoolean(filters, "used");
        String paperSize = filterText(filters, "paperSize");
        String orientation = filterText(filters, "orientation");
        String courseTypeCode = filterText(filters, "courseTypeCode");

        List<CertificateConfigResponse> matched = new ArrayList<>();
        for (CertificateConfig config : visibleConfigs(actor)) {
            List<CertifiedCourseTypeResponse> usedBy = usage.getOrDefault(config.getId(), List.of());
            if (scope != null && !scope.equalsIgnoreCase(CertificateConfigScopeResolver.levelOf(config.getScopeKey()))) continue;
            if (isDefault != null && isDefault != Boolean.TRUE.equals(config.getIsDefault())) continue;
            if (used != null && used != !usedBy.isEmpty()) continue;
            if (paperSize != null && !paperSize.equalsIgnoreCase(config.getPaperSize())) continue;
            if (orientation != null && !orientation.equalsIgnoreCase(config.getOrientation())) continue;
            if (courseTypeCode != null && usedBy.stream().noneMatch(t -> courseTypeCode.equals(t.getTypeCode()))) continue;
            if (!keyword.isEmpty()) {
                boolean hit = foldText(config.getName()).contains(keyword)
                        || usedBy.stream().anyMatch(t -> foldText(t.getTypeName()).contains(keyword) || foldText(t.getTypeCode()).contains(keyword));
                if (!hit) continue;
            }
            matched.add(toSummary(config, actor, usedBy));
        }

        Comparator<CertificateConfigResponse> byName = Comparator.comparing(c -> foldText(c.getName()));
        Comparator<CertificateConfigResponse> order = switch (request.getSortBy() == null ? "" : request.getSortBy()) {
            case "name" -> byName;
            case "updatedAt" -> Comparator.comparing(CertificateConfigResponse::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "usedByCount" -> Comparator.comparing(CertificateConfigResponse::getUsedByCount).thenComparing(byName);
            case "scopeLevel" -> Comparator.comparing(CertificateConfigResponse::getScopeLevel).thenComparing(byName);
            default -> Comparator.comparing((CertificateConfigResponse c) -> !Boolean.TRUE.equals(c.getIsDefault())).thenComparing(byName);
        };
        if (request.getSortBy() != null && "DESC".equalsIgnoreCase(request.getSortDirection())) {
            order = order.reversed();
        }
        matched.sort(order);

        int size = Math.max(1, request.getSize());
        int from = (int) Math.min((long) request.getPage() * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageImpl<>(matched.subList(from, to), PageRequest.of(request.getPage(), size), matched.size());
    }

    private List<CertificateConfig> visibleConfigs(ActorScope actor) {
        return actor.usableKeys() == null
                ? configRepository.findAllConfigs()
                : configRepository.findByScopeKeys(actor.usableKeys());
    }

    private static String filterText(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static Boolean filterBoolean(Map<String, Object> filters, String key) {
        String value = filterText(filters, key);
        return value == null ? null : Boolean.valueOf(value);
    }

    // Chữ thường, bỏ dấu tiếng Việt để tìm kiếm thân thiện.
    private static String foldText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase().trim();
    }

    // Cấu hình hiệu lực của một loại khóa theo phạm vi người xem (fallback cấu hình chung), có thể rỗng.
    @Transactional(readOnly = true)
    public CertificateConfigResponse getResolved(String courseTypeCode, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        return resolve(normalize(courseTypeCode), chainOf(actor))
                .map(config -> toResponse(config, actor, List.of()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CertificateConfigAssignmentResponse> assignments(Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        return assignmentRepository.findByScopeKeys(List.of(actor.key())).stream()
                .map(a -> new CertificateConfigAssignmentResponse(a.getCourseTypeCode(), a.getConfigId()))
                .toList();
    }

    // Tạo cấu hình mới trong phạm vi của người dùng. Trùng tên thì thêm hậu tố số; cấu hình đầu tiên của phạm vi là cấu hình chung.
    @Transactional
    public CertificateConfigResponse create(CertificateConfigUpsertRequest request, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();

        CertificateConfig config = new CertificateConfig();
        config.setId(UUID.randomUUID());
        config.setScopeKey(actor.key());
        config.setName(uniqueName(actor.key(), request.getName(), null));
        config.setIsDefault(configRepository.findDefault(actor.key()).isEmpty());
        config.setCreatedAt(now);
        config.setCreatedBy(audit);
        config.setStatus(Boolean.TRUE);
        applyContent(config, request, now, audit);

        return toResponse(configRepository.persist(config), actor, List.of());
    }

    @Transactional
    public CertificateConfigResponse update(UUID id, CertificateConfigUpsertRequest request, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        CertificateConfig config = requireEditable(id, actor);
        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();

        if (normalize(request.getName()) != null) {
            config.setName(uniqueName(config.getScopeKey(), request.getName(), config.getName()));
        }
        applyContent(config, request, now, audit);
        return toResponse(configRepository.merge(config), actor, usageByConfig(actor).getOrDefault(id, List.of()));
    }

    // Đặt cấu hình làm cấu hình chung của phạm vi chứa nó; cấu hình chung cũ trở thành cấu hình thường.
    @Transactional
    public CertificateConfigResponse setDefault(UUID id, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        CertificateConfig config = requireEditable(id, actor);
        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();

        if (!Boolean.TRUE.equals(config.getIsDefault())) {
            configRepository.findDefault(config.getScopeKey()).ifPresent(current -> {
                current.setIsDefault(Boolean.FALSE);
                current.setUpdatedAt(now);
                current.setUpdatedBy(audit);
                configRepository.merge(current);
            });
            // Chỉ mục duy nhất một cấu hình chung mỗi phạm vi: bỏ cờ cũ xuống DB trước khi đặt cờ mới.
            configRepository.flush();
            config.setIsDefault(Boolean.TRUE);
            config.setUpdatedAt(now);
            config.setUpdatedBy(audit);
            config = configRepository.merge(config);
        }
        return toResponse(config, actor, usageByConfig(actor).getOrDefault(id, List.of()));
    }

    // Xóa cấu hình. Không xóa được cấu hình chung (phải chọn cấu hình khác làm chung trước).
    // Các loại khóa đang được gán cấu hình này bị gỡ gán và quay về dùng cấu hình chung ở lần xuất kế tiếp;
    // chứng nhận đã cấp không bị ảnh hưởng vì PDF được dựng lại từ cấu hình lúc xuất.
    @Transactional
    public void delete(UUID id, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        CertificateConfig config = requireEditable(id, actor);
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            throw validationHelper.conflict(
                    "Không thể xóa cấu hình chung. Hãy chọn một cấu hình khác làm cấu hình chung trước khi xóa.");
        }
        configRepository.remove(config.getId());
    }

    // Gán cấu hình cho loại khóa trong phạm vi của người dùng; configId = null thì bỏ gán (dùng cấu hình chung).
    @Transactional
    public void assign(CertificateConfigAssignRequest request, Authentication authentication) {
        ActorScope actor = scopeResolver.resolve(authentication);
        String typeCode = normalize(request.getCourseTypeCode());
        TrainingCourseType type = courseTypeRepository.findByCodeWithPrerequisite(typeCode)
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy loại khóa: " + typeCode));
        if (type.getResultingLevel() == null || "NONE".equals(type.getResultingLevel().name())) {
            throw validationHelper.conflict("Loại khóa này không có yêu cầu chứng nhận nên không cấu hình được");
        }

        Optional<CertificateConfigAssignment> existing = assignmentRepository.findByTypeAndScope(typeCode, actor.key());
        if (request.getConfigId() == null) {
            existing.ifPresent(a -> assignmentRepository.remove(a.getId()));
            return;
        }
        CertificateConfig config = configRepository.findById(request.getConfigId())
                .filter(c -> actor.canSee(c.getScopeKey()))
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấu hình chứng nhận"));

        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();
        CertificateConfigAssignment assignment = existing.orElseGet(() -> {
            CertificateConfigAssignment created = new CertificateConfigAssignment();
            created.setId(UUID.randomUUID());
            created.setCourseTypeCode(typeCode);
            created.setScopeKey(actor.key());
            created.setCreatedAt(now);
            created.setCreatedBy(audit);
            created.setStatus(Boolean.TRUE);
            return created;
        });
        assignment.setConfigId(config.getId());
        assignment.setUpdatedAt(now);
        assignment.setUpdatedBy(audit);
        if (existing.isPresent()) {
            assignmentRepository.merge(assignment);
        } else {
            assignmentRepository.persist(assignment);
        }
    }

    private CertificateConfig requireEditable(UUID id, ActorScope actor) {
        CertificateConfig config = configRepository.findById(id)
                .filter(c -> actor.canSee(c.getScopeKey()))
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấu hình chứng nhận"));
        if (!actor.canEdit(config.getScopeKey())) {
            throw validationHelper.forbidden("Cấu hình này thuộc cấp khác, bạn chỉ được xem và dùng, không được sửa hoặc xóa");
        }
        return config;
    }

    private void applyContent(CertificateConfig config, CertificateConfigUpsertRequest request, OffsetDateTime now, String audit) {
        config.setPaperSize(request.getPaperSize() == null || request.getPaperSize().isBlank() ? "A4" : request.getPaperSize().trim().toUpperCase());
        config.setOrientation(request.getOrientation() == null || request.getOrientation().isBlank() ? "LANDSCAPE" : request.getOrientation().trim().toUpperCase());
        config.setBackgroundImage(request.getBackgroundImage());
        config.setFieldLayout(request.getFieldLayout() == null ? new ArrayList<>() : request.getFieldLayout());
        config.setUpdatedAt(now);
        config.setUpdatedBy(audit);
    }

    // Tên duy nhất (không phân biệt hoa thường) trong phạm vi; trùng thì thêm " (1)", " (2)"... như Windows.
    // currentName: tên hiện tại của chính cấu hình đang sửa, không tính là trùng với chính nó.
    private String uniqueName(String scopeKey, String requested, String currentName) {
        String base = normalize(requested);
        if (base == null) {
            base = "Cấu hình chứng nhận";
        }
        if (base.length() > 200) {
            base = base.substring(0, 200).trim();
        }
        Set<String> taken = new HashSet<>();
        for (String name : configRepository.findNamesInScope(scopeKey)) {
            taken.add(name.toLowerCase());
        }
        if (currentName != null) {
            taken.remove(currentName.toLowerCase());
        }
        String candidate = base;
        for (int n = 1; taken.contains(candidate.toLowerCase()); n++) {
            candidate = base + " (" + n + ")";
        }
        return candidate;
    }

    // Chuỗi phạm vi tra cứu theo thứ tự ưu tiên: phạm vi của chính mình -> cấp trên -> hệ thống.
    private List<String> chainOf(ActorScope actor) {
        return actor.usableKeys() == null ? List.of(CertificateConfigScopeResolver.SYSTEM) : actor.usableKeys();
    }

    // Chuỗi phạm vi của một khóa học khi xuất chứng nhận: giáo hạt (nếu khóa do giáo hạt quản lý) -> giáo phận -> hệ thống.
    private List<String> chainOf(TrainingCourse course) {
        List<String> chain = new ArrayList<>();
        if (course == null) {
            chain.add(CertificateConfigScopeResolver.SYSTEM);
            return chain;
        }
        boolean deaneryManaged = course.getCourseType() != null
                && course.getCourseType().getManagementUnitType() == UnitType.DEANERY;
        var deanery = course.getDeanery();
        UUID dioceseId = course.getDiocese() != null ? course.getDiocese().getId()
                : (deanery != null && deanery.getDiocese() != null ? deanery.getDiocese().getId() : null);
        if (deaneryManaged && deanery != null) {
            chain.add(CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DEANERY, deanery.getId()));
        }
        if (dioceseId != null) {
            chain.add(CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DIOCESE, dioceseId));
        }
        chain.add(CertificateConfigScopeResolver.SYSTEM);
        return chain;
    }

    // Resolve: trong từng phạm vi theo thứ tự ưu tiên, dùng cấu hình được gán cho loại khóa, nếu không có thì cấu hình chung của phạm vi đó.
    private Optional<CertificateConfig> resolve(String courseTypeCode, List<String> chain) {
        for (String scopeKey : chain) {
            if (courseTypeCode != null) {
                Optional<CertificateConfig> assigned = assignmentRepository.findByTypeAndScope(courseTypeCode, scopeKey)
                        .flatMap(a -> configRepository.findById(a.getConfigId()));
                if (assigned.isPresent()) {
                    return assigned;
                }
            }
            Optional<CertificateConfig> fallback = configRepository.findDefault(scopeKey);
            if (fallback.isPresent()) {
                return fallback;
            }
        }
        return Optional.empty();
    }

    // Các loại khóa có yêu cầu chứng nhận đang dùng từng cấu hình (theo cùng quy tắc resolve), tính theo phạm vi người xem.
    private Map<UUID, List<CertifiedCourseTypeResponse>> usageByConfig(ActorScope actor) {
        List<String> chain = chainOf(actor);
        Map<String, Map<String, UUID>> assigned = new HashMap<>();
        for (CertificateConfigAssignment a : assignmentRepository.findByScopeKeys(chain)) {
            assigned.computeIfAbsent(a.getScopeKey(), k -> new HashMap<>()).put(a.getCourseTypeCode(), a.getConfigId());
        }
        Map<String, UUID> defaults = new HashMap<>();
        for (CertificateConfig c : configRepository.findByScopeKeys(chain)) {
            if (Boolean.TRUE.equals(c.getIsDefault())) {
                defaults.put(c.getScopeKey(), c.getId());
            }
        }

        Map<UUID, List<CertifiedCourseTypeResponse>> usage = new LinkedHashMap<>();
        for (TrainingCourseType type : courseTypeRepository.findCertifiedTypes()) {
            UUID effective = null;
            for (String scopeKey : chain) {
                UUID configId = assigned.getOrDefault(scopeKey, Map.of()).get(type.getTypeCode());
                if (configId == null) {
                    configId = defaults.get(scopeKey);
                }
                if (configId != null) {
                    effective = configId;
                    break;
                }
            }
            if (effective != null) {
                usage.computeIfAbsent(effective, k -> new ArrayList<>())
                        .add(new CertifiedCourseTypeResponse(type.getTypeCode(), type.getTypeName()));
            }
        }
        return usage;
    }

    @Transactional(readOnly = true)
    public PdfExport exportPdf(List<String> certificateCodes) {
        List<Certificate> certificates = new ArrayList<>();
        List<CertificatePdfRenderer.RenderPage> pages = new ArrayList<>();

        // Chỉ xuất chứng nhận đã duyệt; báo rõ chứng nhận nào chưa duyệt / bị từ chối để người dùng bỏ ra.
        List<Certificate> loaded = new ArrayList<>();
        List<String> notApproved = new ArrayList<>();
        for (String code : certificateCodes) {
            Certificate certificate = certificateRepository.findByCodeWithLeaderAndCourse(code)
                    .orElseThrow(() -> validationHelper.notFound("Không tìm thấy chứng nhận: " + code));
            loaded.add(certificate);
            if (certificate.getApprovalStatus() != WorkflowApprovalStatus.APPROVED) {
                String leaderName = buildFullName(certificate.getLeader());
                String status = certificate.getApprovalStatus() == WorkflowApprovalStatus.REJECTED ? "bị từ chối" : "chưa duyệt";
                notApproved.add((leaderName.isBlank() ? code : leaderName + " - " + code) + " (" + status + ")");
            }
        }
        if (!notApproved.isEmpty()) {
            throw validationHelper.validationError(
                    "Chỉ xuất được chứng nhận đã duyệt. Vui lòng bỏ các chứng nhận sau: " + String.join("; ", notApproved));
        }

        for (Certificate certificate : loaded) {
            String code = certificate.getCertificateCode();
            TrainingCourse course = certificate.getCourse();
            String courseTypeCode = course != null && course.getCourseType() != null
                    ? course.getCourseType().getTypeCode() : null;
            CertificateConfig config = resolve(normalize(courseTypeCode), chainOf(course))
                    .orElseThrow(() -> validationHelper.validationError(
                            "Chưa có cấu hình chứng nhận (riêng hoặc chung) cho chứng nhận: " + code));

            Map<String, String> values = buildValues(certificate, config);
            float[] dims = pageDimensions(config.getPaperSize(), config.getOrientation());
            pages.add(new CertificatePdfRenderer.RenderPage(
                    decodeBackground(config.getBackgroundImage()),
                    toRenderItems(config.getFieldLayout(), values),
                    dims[0], dims[1]));
            certificates.add(certificate);
        }

        byte[] content;
        try {
            content = pdfRenderer.renderPdf(pages);
        } catch (Exception e) {
            // Tránh lỗi 500 chung chung "không khả dụng"; trả thông báo rõ ràng.
            throw validationHelper.validationError("Không thể tạo PDF chứng nhận: " + e.getMessage());
        }
        return new PdfExport(content, buildFilename(certificates));
    }

    // Kích thước trang (điểm) theo khổ giấy + hướng in. Mặc định A4 ngang.
    private float[] pageDimensions(String paperSize, String orientation) {
        String size = paperSize == null ? "A4" : paperSize.trim().toUpperCase();
        float w;
        float h;
        switch (size) {
            case "A3" -> { w = 841.89f; h = 1190.55f; }
            case "A5" -> { w = 419.53f; h = 595.28f; }
            case "LETTER" -> { w = 612f; h = 792f; }
            case "LEGAL" -> { w = 612f; h = 1008f; }
            default -> { w = 595.28f; h = 841.89f; } // A4
        }
        boolean landscape = orientation == null || "LANDSCAPE".equalsIgnoreCase(orientation.trim());
        return landscape ? new float[]{h, w} : new float[]{w, h};
    }

    private List<CertificatePdfRenderer.RenderItem> toRenderItems(List<CertificateLayoutItem> layout, Map<String, String> values) {
        List<CertificatePdfRenderer.RenderItem> items = new ArrayList<>();
        if (layout == null) {
            return items;
        }
        for (CertificateLayoutItem item : layout) {
            String text = "TEXT".equalsIgnoreCase(item.getType())
                    ? item.getText()
                    : values.getOrDefault(item.getFieldKey(), "");
            if (text == null || text.isBlank()) {
                continue;
            }
            items.add(new CertificatePdfRenderer.RenderItem(
                    text,
                    item.getX() == null ? 0 : item.getX(),
                    item.getY() == null ? 0 : item.getY(),
                    item.getFontSize() == null ? 0 : item.getFontSize(),
                    item.getColor(),
                    item.getAlign(),
                    item.getFontFamily(),
                    Boolean.TRUE.equals(item.getBold()),
                    Boolean.TRUE.equals(item.getItalic()),
                    Boolean.TRUE.equals(item.getUnderline())));
        }
        return items;
    }

    private Map<String, String> buildValues(Certificate c, CertificateConfig config) {
        Map<String, String> v = new LinkedHashMap<>();
        Leader l = c.getLeader();
        TrainingCourse course = c.getCourse();

        // Người ký lấy trực tiếp từ chứng nhận (xác định khi duyệt).
        v.put("signerName", nullToEmpty(c.getSignerName()));
        v.put("signerTitle", nullToEmpty(c.getSignerTitle()));

        if (l != null) {
            v.put("leaderFullName", buildFullName(l));
            v.put("leaderBirthDate", l.getBirthDate() == null ? "" : l.getBirthDate().format(DATE_FORMAT));
            v.put("leaderGender", l.getGender() == null ? "" : ("NAM".equals(l.getGender().name()) ? "Nam" : "Nữ"));
            var parish = l.getParish();
            var deanery = parish == null ? null : parish.getDeanery();
            var diocese = deanery == null ? null : deanery.getDiocese();
            // Giáo xứ dùng tên giáo xứ (name), không phải xứ đoàn (chapterName).
            v.put("leaderParishName", parish == null ? "" : nullToEmpty(parish.getName()));
            v.put("leaderDeaneryName", deanery == null ? "" : nullToEmpty(deanery.getName()));
            v.put("leaderDioceseName", diocese == null ? "" : nullToEmpty(diocese.getName()));
            v.put("leaderLevel", l.getLeaderLevel() == null ? "" : LEVEL_LABELS.getOrDefault(l.getLeaderLevel().name(), l.getLeaderLevel().name()));
        }
        if (course != null) {
            v.put("courseName", nullToEmpty(course.getCourseName()));
            v.put("courseCode", nullToEmpty(course.getCourseCode()));
            v.put("hostYear", course.getHostYear() == null ? "" : String.valueOf(course.getHostYear()));
            String start = course.getStartDate() == null ? "" : course.getStartDate().format(DATE_FORMAT);
            String end = course.getEndDate() == null ? "" : course.getEndDate().format(DATE_FORMAT);
            v.put("courseStartDate", start);
            v.put("courseEndDate", end);
            // Thời gian tổ chức: "từ ... đến ..." (rút gọn khi thiếu một đầu).
            v.put("coursePeriod", !start.isEmpty() && !end.isEmpty() ? ("từ " + start + " đến " + end) : (start + end));
            var courseType = course.getCourseType();
            v.put("resultingLevel", courseType == null || courseType.getResultingLevel() == null
                    ? ""
                    : LEVEL_LABELS.getOrDefault(courseType.getResultingLevel().name(), courseType.getResultingLevel().name()));
        }
        v.put("certificateCode", nullToEmpty(c.getCertificateCode()));
        v.put("certificateName", nullToEmpty(c.getCertificateName()));
        v.put("result", nullToEmpty(c.getResult()));
        v.put("issueDate", c.getIssueDate() == null ? "" : c.getIssueDate().format(DATE_FORMAT));
        return v;
    }

    private String buildFilename(List<Certificate> certificates) {
        if (certificates.size() == 1) {
            Certificate c = certificates.get(0);
            String leaderName = buildFullName(c.getLeader());
            if (leaderName.isBlank()) {
                leaderName = "Huynh truong";
            }
            return sanitizeFilename(leaderName + "-" + c.getCertificateCode()) + ".pdf";
        }
        String commonCourse = null;
        for (Certificate c : certificates) {
            String name = c.getCourse() == null ? null : c.getCourse().getCourseName();
            if (name == null) {
                commonCourse = null;
                break;
            }
            if (commonCourse == null) {
                commonCourse = name;
            } else if (!commonCourse.equals(name)) {
                commonCourse = null;
                break;
            }
        }
        String base = commonCourse == null ? "Chứng nhận" : "Chứng nhận - " + commonCourse;
        return sanitizeFilename(base) + ".pdf";
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", " ").trim();
    }

    private byte[] decodeBackground(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        String base64 = dataUrl;
        int comma = dataUrl.indexOf(',');
        if (dataUrl.startsWith("data:") && comma >= 0) {
            base64 = dataUrl.substring(comma + 1);
        }
        try {
            return java.util.Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CertificateConfigResponse toResponse(CertificateConfig config, ActorScope actor, List<CertifiedCourseTypeResponse> usedBy) {
        return summaryBuilder(config, actor, usedBy)
                .backgroundImage(config.getBackgroundImage())
                .fieldLayout(config.getFieldLayout())
                .build();
    }

    // Dạng gọn cho danh sách: không kèm ảnh nền base64 và bố cục (lấy riêng qua chi tiết khi cần).
    private CertificateConfigResponse toSummary(CertificateConfig config, ActorScope actor, List<CertifiedCourseTypeResponse> usedBy) {
        return summaryBuilder(config, actor, usedBy).build();
    }

    private CertificateConfigResponse.CertificateConfigResponseBuilder summaryBuilder(
            CertificateConfig config, ActorScope actor, List<CertifiedCourseTypeResponse> usedBy) {
        return CertificateConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .isDefault(config.getIsDefault())
                .scopeLevel(CertificateConfigScopeResolver.levelOf(config.getScopeKey()))
                .editable(actor.canEdit(config.getScopeKey()))
                .paperSize(config.getPaperSize())
                .orientation(config.getOrientation())
                .status(config.getStatus())
                .updatedAt(config.getUpdatedAt())
                .usedByCourseTypes(usedBy)
                .usedByCount(usedBy.size());
    }

    // Gộp tên thánh + họ và tên (vd "Giuse Nguyễn Văn A").
    private String buildFullName(Leader leader) {
        if (leader == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (leader.getHolyName() != null && !leader.getHolyName().isBlank()) {
            sb.append(leader.getHolyName().trim());
        }
        if (leader.getLastName() != null && !leader.getLastName().isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(leader.getLastName().trim());
        }
        if (leader.getFirstName() != null && !leader.getFirstName().isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(leader.getFirstName().trim());
        }
        return sb.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
