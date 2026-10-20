package phucuong.ht.modules.certificate.config.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.config.model.CertificateMergeField;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfig;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificateFieldCatalogItem;
import phucuong.ht.modules.certificate.config.model.response.CertifiedCourseTypeResponse;
import phucuong.ht.modules.certificate.config.repository.CertificateConfigRepository;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.enums.CertificateSignerType;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Transactional(readOnly = true)
    public List<CertificateConfigResponse> list() {
        return configRepository.findAllConfigs().stream().map(this::toResponse).toList();
    }

    // Trả cấu hình đã resolve theo loại khóa (fallback cấu hình chung), có thể rỗng.
    @Transactional(readOnly = true)
    public CertificateConfigResponse getResolved(String courseTypeCode) {
        return resolve(courseTypeCode).map(this::toResponse).orElse(null);
    }

    @Transactional
    public CertificateConfigResponse upsert(CertificateConfigUpsertRequest request, Authentication authentication) {
        if (request.getSignerType() == CertificateSignerType.OTHER
                && (request.getSignerTitle() == null || request.getSignerTitle().isBlank())) {
            throw validationHelper.validationError("Phải nhập chức danh khi người ký là 'Khác'");
        }

        String courseTypeCode = normalize(request.getCourseTypeCode());
        if (courseTypeCode != null) {
            TrainingCourseType type = courseTypeRepository.findByCodeWithPrerequisite(courseTypeCode)
                    .orElseThrow(() -> validationHelper.notFound("Không tìm thấy loại khóa: " + courseTypeCode));
            if (type.getResultingLevel() == null || "NONE".equals(type.getResultingLevel().name())) {
                throw validationHelper.conflict("Loại khóa này không có yêu cầu chứng nhận nên không cấu hình được");
            }
        }

        String actor = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();
        Optional<CertificateConfig> existing = courseTypeCode == null
                ? configRepository.findDefaultConfig()
                : configRepository.findByCourseTypeCode(courseTypeCode);

        CertificateConfig config = existing.orElseGet(() -> {
            CertificateConfig created = new CertificateConfig();
            created.setId(UUID.randomUUID());
            created.setCourseTypeCode(courseTypeCode);
            created.setCreatedAt(now);
            created.setCreatedBy(actor);
            created.setStatus(Boolean.TRUE);
            return created;
        });

        config.setSignerType(request.getSignerType());
        config.setSignerName(request.getSignerName());
        config.setSignerTitle(request.getSignerType() == CertificateSignerType.OTHER ? request.getSignerTitle() : null);
        config.setBackgroundImage(request.getBackgroundImage());
        config.setFieldLayout(request.getFieldLayout() == null ? new ArrayList<>() : request.getFieldLayout());
        config.setUpdatedAt(now);
        config.setUpdatedBy(actor);

        CertificateConfig saved = existing.isPresent() ? configRepository.merge(config) : configRepository.persist(config);
        return toResponse(saved);
    }

    @Transactional
    public void delete(String courseTypeCode) {
        String code = normalize(courseTypeCode);
        Optional<CertificateConfig> existing = code == null
                ? configRepository.findDefaultConfig()
                : configRepository.findByCourseTypeCode(code);
        existing.ifPresent(config -> configRepository.remove(config.getId()));
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
            CertificateConfig config = resolve(courseTypeCode)
                    .orElseThrow(() -> validationHelper.validationError(
                            "Chưa có cấu hình chứng nhận (riêng hoặc chung) cho chứng nhận: " + code));

            Map<String, String> values = buildValues(certificate, config);
            pages.add(new CertificatePdfRenderer.RenderPage(
                    decodeBackground(config.getBackgroundImage()),
                    toRenderItems(config.getFieldLayout(), values)));
            certificates.add(certificate);
        }

        byte[] content = pdfRenderer.renderPdf(pages);
        return new PdfExport(content, buildFilename(certificates));
    }

    // Resolve: ưu tiên cấu hình riêng của loại khóa, nếu không có thì dùng cấu hình chung.
    private Optional<CertificateConfig> resolve(String courseTypeCode) {
        String code = normalize(courseTypeCode);
        if (code != null) {
            Optional<CertificateConfig> specific = configRepository.findByCourseTypeCode(code);
            if (specific.isPresent()) {
                return specific;
            }
        }
        return configRepository.findDefaultConfig();
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

        String signerTitle = config.getSignerType() == CertificateSignerType.CHAPLAIN
                ? "Linh mục tuyên úy"
                : nullToEmpty(config.getSignerTitle());
        v.put("signerName", nullToEmpty(config.getSignerName()));
        v.put("signerTitle", signerTitle);

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

    private CertificateConfigResponse toResponse(CertificateConfig config) {
        String courseTypeName = null;
        if (config.getCourseTypeCode() != null) {
            courseTypeName = courseTypeRepository.findByCodeWithPrerequisite(config.getCourseTypeCode())
                    .map(TrainingCourseType::getTypeName).orElse(null);
        }
        return CertificateConfigResponse.builder()
                .id(config.getId())
                .courseTypeCode(config.getCourseTypeCode())
                .courseTypeName(courseTypeName)
                .signerType(config.getSignerType())
                .signerName(config.getSignerName())
                .signerTitle(config.getSignerTitle())
                .backgroundImage(config.getBackgroundImage())
                .fieldLayout(config.getFieldLayout())
                .status(config.getStatus())
                .build();
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
