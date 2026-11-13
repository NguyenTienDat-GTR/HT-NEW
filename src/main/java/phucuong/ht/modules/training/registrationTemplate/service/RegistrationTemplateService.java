package phucuong.ht.modules.training.registrationTemplate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.storage.MediaStorageException;
import phucuong.ht.modules.media.storage.MediaStorageProvider;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;
import phucuong.ht.modules.training.registrationTemplate.model.RegistrationTemplateField;
import phucuong.ht.modules.training.registrationTemplate.model.entity.RegistrationExportTemplate;
import phucuong.ht.modules.training.registrationTemplate.repository.RegistrationExportTemplateRepository;
import phucuong.ht.modules.training.registrationTemplate.security.RegistrationTemplateAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Quản lý mẫu danh sách đăng ký: mỗi đơn vị (giáo phận hoặc giáo hạt) một mẫu, là file Excel do người dùng tự soạn
 * trên trình duyệt (trường chèn dạng {{tên trường}}). Tài khoản chỉ làm việc với mẫu của đơn vị mình; super admin chỉ định đơn vị.
 * Khóa cấp giáo hạt dùng mẫu của giáo hạt, chưa có thì dùng mẫu của giáo phận.
 */
@Service
@RequiredArgsConstructor
public class RegistrationTemplateService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RegistrationTemplateService.class);

    public static final long MAX_TEMPLATE_BYTES = 5L * 1024 * 1024;
    public static final String DIOCESE = "DIOCESE";
    public static final String DEANERY = "DEANERY";

    private final RegistrationExportTemplateRepository repository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final AccountRepository accountRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final RegistrationTemplateAccessEvaluator accessEvaluator;
    private final securityUtils securityUtils;
    private final MediaStorageRegistry storage;
    private final MediaProperties mediaProperties;

    public record FieldResponse(String key, String label, String group, String kind) {
    }

    public record TemplateResponse(String unitType, UUID unitId, String unitName, boolean configured, String fileName,
                                   OffsetDateTime updatedAt, String updatedBy) {
    }

    public record TemplateFile(String fileName, byte[] content) {
    }

    /** Dữ liệu minh họa do trình soạn gửi lên để dựng file xem trước: giá trị các trường ô và các dòng của bảng (trường cột -> giá trị). */
    public record PreviewData(java.util.Map<String, String> values, List<java.util.Map<String, String>> rows) {
    }

    /** Mẫu dùng khi xuất cho một khóa. */
    public record ExportTemplate(TrainingCourse course, byte[] content) {
    }

    private record Unit(String type, UUID id, String name) {
    }

    public List<FieldResponse> fields() {
        List<FieldResponse> fields = new ArrayList<>();
        for (RegistrationTemplateField field : RegistrationTemplateField.values()) {
            fields.add(new FieldResponse(field.getKey(), field.getLabel(), field.getGroup(), field.getKind().name()));
        }
        return fields;
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(String unitType, UUID unitId, Authentication authentication) {
        Unit unit = resolveUnit(unitType, unitId, authentication, false);
        return repository.findByUnitTypeAndUnitId(unit.type(), unit.id())
                .map(template -> toResponse(unit, template))
                .orElseGet(() -> new TemplateResponse(unit.type(), unit.id(), unit.name(), false, null, null, null));
    }

    @Transactional(readOnly = true)
    public TemplateFile file(String unitType, UUID unitId, Authentication authentication) {
        Unit unit = resolveUnit(unitType, unitId, authentication, false);
        RegistrationExportTemplate template = repository.findByUnitTypeAndUnitId(unit.type(), unit.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Đơn vị chưa có mẫu danh sách đăng ký"));
        return new TemplateFile(template.getFileName(), read(template));
    }

    /** Khóa cố định theo đơn vị: lưu bản mới ghi đè đúng file cũ trên kho lưu trữ. */
    private String keyOf(Unit unit) {
        return mediaProperties.getRootPrefix() + "/registration-templates/" + unit.type().toLowerCase(Locale.ROOT) + "/" + unit.id() + "/mau-danh-sach-dang-ky.xlsx";
    }

    private byte[] read(RegistrationExportTemplate template) {
        try {
            // Kho lưu trữ chỉ có bản nén: giải nén ra file gốc để sửa / xuất.
            return TemplateCompression.decompress(storage.require(template.getStorageProvider()).getFile(template.getStorageKey()));
        } catch (MediaStorageException | java.io.UncheckedIOException exception) {
            log.warn("Không đọc được file mẫu danh sách đăng ký {} từ kho lưu trữ {}", template.getStorageKey(), template.getStorageProvider(), exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode.INTERNAL_ERROR, "Không đọc được file mẫu từ kho lưu trữ. Vui lòng thử lại");
        }
    }

    /** Lưu file mẫu (toàn bộ file do trình soạn trên trình duyệt tạo ra); kiểm tra file đọc được và mọi {{trường}} đều hợp lệ. */
    @Transactional
    public TemplateResponse save(String unitType, UUID unitId, MultipartFile file, Authentication authentication) {
        Unit unit = resolveUnit(unitType, unitId, authentication, true);
        byte[] content = readUpload(file);
        try {
            RegistrationTemplateRenderer.validateTemplate(content);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        RegistrationExportTemplate template = repository.findByUnitTypeAndUnitId(unit.type(), unit.id()).orElseGet(() -> {
            RegistrationExportTemplate created = new RegistrationExportTemplate();
            created.setId(UUID.randomUUID());
            created.setUnitType(unit.type());
            created.setUnitId(unit.id());
            created.setStatus(Boolean.TRUE);
            return created;
        });
        String key = keyOf(unit);
        String previousProvider = template.getStorageProvider();
        String previousKey = template.getStorageKey();
        MediaStorageProvider provider = storage.active();
        byte[] compressed;
        try {
            // Cùng khóa nên ghi đè bản cũ; chỉ khi đổi nhà cung cấp / khóa mới phải xóa bản ở chỗ cũ.
            compressed = TemplateCompression.compress(content);
            provider.putFile(key, compressed, "application/octet-stream");
        } catch (MediaStorageException exception) {
            log.warn("Không lưu được file mẫu danh sách đăng ký {} lên kho lưu trữ {}", key, provider.name(), exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorCode.INTERNAL_ERROR, "Không lưu được file mẫu lên kho lưu trữ. Vui lòng thử lại");
        }
        template.setFileName(cleanFileName(file.getOriginalFilename()));
        template.setStorageProvider(provider.name());
        template.setStorageKey(key);
        template.setFileSize((long) content.length);
        template.setCompressedSize((long) compressed.length);
        RegistrationExportTemplate saved = repository.save(template);
        if (previousProvider != null && previousKey != null && (!previousProvider.equals(provider.name()) || !previousKey.equals(key))) {
            deleteQuietly(previousProvider, previousKey);
        }
        return toResponse(unit, saved);
    }

    /**
     * Dựng file Excel xem trước từ mẫu đang soạn (chưa cần lưu) và dữ liệu minh họa, bằng đúng bộ dựng khi xuất thật nên giống từng chi tiết
     * (dòng bảng nhân lên, ô gộp, khổ giấy, ảnh logo...). Không ghi gì vào hệ thống.
     */
    public TemplateFile preview(MultipartFile file, PreviewData data) {
        byte[] content = readUpload(file);
        try {
            RegistrationTemplateRenderer.validateTemplate(content);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        if (!RegistrationTemplateRenderer.hasDataRow(content)) {
            throw invalid("Mẫu chưa có dòng dữ liệu. Trong mẫu, đặt các trường như Họ và tên, Ngày sinh vào cùng một dòng của bảng");
        }
        java.util.Map<String, String> values = data == null || data.values() == null ? java.util.Map.of() : data.values();
        List<java.util.Map<String, String>> rows = data == null || data.rows() == null ? List.of() : data.rows();
        if (values.size() > 300 || rows.size() > 50 || rows.stream().anyMatch(row -> row == null || row.size() > 100)) {
            throw invalid("Dữ liệu xem trước quá lớn");
        }
        List<java.util.Map<String, Object>> tableRows = new ArrayList<>();
        for (java.util.Map<String, String> row : rows) {
            tableRows.add(new java.util.HashMap<>(row));
        }
        RegistrationTemplateRenderer.Group group = new RegistrationTemplateRenderer.Group("Xem trước", java.util.Map.of(), tableRows);
        byte[] result = RegistrationTemplateRenderer.renderWorkbook(content, values, List.of(group), RegistrationTemplateRenderer.Layout.SINGLE_SHEET, false);
        return new TemplateFile("xem-truoc-mau-danh-sach-dang-ky.xlsx", result);
    }

    @Transactional
    public void delete(String unitType, UUID unitId, Authentication authentication) {
        Unit unit = resolveUnit(unitType, unitId, authentication, true);
        repository.findByUnitTypeAndUnitId(unit.type(), unit.id()).ifPresent(template -> {
            repository.delete(template);
            deleteQuietly(template.getStorageProvider(), template.getStorageKey());
        });
    }

    private void deleteQuietly(String provider, String key) {
        try {
            if (storage.has(provider)) {
                storage.require(provider).deleteFile(key);
            }
        } catch (MediaStorageException ignored) {
            // file mồ côi trên kho lưu trữ không ảnh hưởng dữ liệu; lần lưu sau cùng khóa sẽ ghi đè
        }
    }

    /** Mẫu dùng cho khóa: của giáo hạt tổ chức khóa (nếu có), rồi tới của giáo phận. */
    @Transactional(readOnly = true)
    public ExportTemplate forCourse(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            throw invalid("Hãy mở danh sách đăng ký của một khóa để xuất theo mẫu");
        }
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khóa huấn luyện"));
        RegistrationExportTemplate template = null;
        if (course.getDeanery() != null) {
            template = repository.findByUnitTypeAndUnitId(DEANERY, course.getDeanery().getId()).orElse(null);
        }
        if (template == null && course.getDiocese() != null) {
            template = repository.findByUnitTypeAndUnitId(DIOCESE, course.getDiocese().getId()).orElse(null);
        }
        if (template == null) {
            throw invalid("Chưa có mẫu danh sách đăng ký cho khóa này. Vào Huấn luyện > Mẫu danh sách đăng ký để soạn mẫu");
        }
        // Chạm các quan hệ cần dùng khi xuất (job chạy ngoài transaction này).
        if (course.getCourseType() != null) {
            course.getCourseType().getTypeName();
        }
        if (course.getDiocese() != null) {
            course.getDiocese().getName();
        }
        if (course.getDeanery() != null) {
            course.getDeanery().getName();
            course.getDeanery().getAssociationName();
        }
        return new ExportTemplate(course, read(template));
    }

    // ---- phạm vi ----

    private Unit resolveUnit(String requestedType, UUID requestedId, Authentication authentication, boolean write) {
        String type = requestedType == null || requestedType.isBlank() ? null : requestedType.toUpperCase(Locale.ROOT);
        if (type != null && !DIOCESE.equals(type) && !DEANERY.equals(type)) {
            throw invalid("Cấp đơn vị không hợp lệ");
        }
        UUID id;
        if (accessEvaluator.isSuperAdmin(authentication)) {
            if (type == null || requestedId == null) {
                throw invalid("Hãy chọn giáo phận hoặc giáo hạt");
            }
            id = requestedId;
        } else {
            String username = securityUtils.currentUsername(authentication);
            UUID dioceseId = username == null ? null : accountRepository.findDirectActiveDioceseIdByUsername(username).orElse(null);
            // Tài khoản giáo hạt gắn qua huynh trưởng (không có cột deanery trực tiếp) nên giáo hạt phải suy ra như các màn khác;
            // chỉ giáo phận mới gắn trực tiếp vào tài khoản.
            UUID deaneryId = username == null ? null : accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
            String action = write ? "update" : "read";
            boolean diocese = dioceseId != null && (accessEvaluator.has(authentication, action, "diocese") || (!write && accessEvaluator.has(authentication, "update", "diocese")));
            boolean deanery = deaneryId != null && (accessEvaluator.has(authentication, action, "deanery") || (!write && accessEvaluator.has(authentication, "update", "deanery")));
            if (type == null) {
                type = diocese ? DIOCESE : deanery ? DEANERY : null;
            }
            if (DIOCESE.equals(type) && diocese) {
                id = dioceseId;
            } else if (DEANERY.equals(type) && deanery) {
                id = deaneryId;
            } else {
                throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Bạn chỉ quản lý được mẫu của đơn vị mình");
            }
            if (requestedId != null && !requestedId.equals(id)) {
                throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Bạn chỉ quản lý được mẫu của đơn vị mình");
            }
        }
        String name = DIOCESE.equals(type)
                ? "Giáo phận " + dioceseRepository.findById(id).orElseThrow(() -> notFound("giáo phận")).getName()
                : "Giáo hạt " + deaneryRepository.findById(id).orElseThrow(() -> notFound("giáo hạt")).getName();
        return new Unit(type, id, name);
    }

    private static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy " + what);
    }

    private static TemplateResponse toResponse(Unit unit, RegistrationExportTemplate template) {
        return new TemplateResponse(unit.type(), unit.id(), unit.name(), true, template.getFileName(), template.getUpdatedAt(), template.getUpdatedBy());
    }

    private static byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Chưa có nội dung mẫu để lưu");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw invalid("File mẫu vượt quá 5MB (thử giảm kích thước ảnh logo)");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không đọc được file tải lên", exception);
        }
    }

    private static String cleanFileName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/]+", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "mau-danh-sach-dang-ky.xlsx";
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }
}
