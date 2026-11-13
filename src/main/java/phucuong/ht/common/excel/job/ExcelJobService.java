package phucuong.ht.common.excel.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import phucuong.ht.common.excel.DuplicateDecision;
import phucuong.ht.common.excel.ImportDuplicate;
import phucuong.ht.common.excel.ImportError;
import phucuong.ht.common.excel.AvatarWarning;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.idempotency.IdempotencyService;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.exception.ApiException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tạo / xem / hủy / tải kết quả tác vụ nền Excel.
 *
 * <p>Idempotency: (người tạo, loại, Idempotency-Key) là duy nhất. Gửi lại cùng key trả lại đúng job cũ; cùng key khác nội dung bị từ chối (409).
 * Mỗi người chỉ có một job đang chạy / chờ cho cùng (loại, đích): ràng buộc partial unique của DB là chốt chặn cuối cho race.
 */
@Service
@RequiredArgsConstructor
public class ExcelJobService {

    public static final int RETENTION_DAYS = 3;
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;
    private static final long MAX_IMAGES_BYTES = 50L * 1024 * 1024;

    private final ExcelJobRepository repository;
    private final ObjectMapper objectMapper;
    private final securityUtils securityUtils;
    private final ExcelJobWorker worker;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.excel-jobs.dir:${java.io.tmpdir}/ht-excel-jobs}")
    private String baseDir;

    // ---- tạo job ----

    public ExcelJobResponse createExport(ExcelJobKind kind, BaseSearchRequest request, String idempotencyKey, Authentication authentication) {
        String key = IdempotencyService.requireValidKey(idempotencyKey);
        String owner = owner(authentication);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("search", request.getSearch());
        params.put("searchFields", request.getSearchFields());
        params.put("filters", request.getFilters());
        params.put("status", request.getStatus());
        params.put("sortBy", request.getSortBy());
        params.put("sortDirection", request.getSortDirection());
        ExportSelection selection = ExportSelection.fromCurrentRequest();
        if (!selection.columns().isEmpty()) {
            params.put("columns", selection.columns());
        }
        if (!selection.options().isEmpty()) {
            params.put("options", selection.options());
        }
        String paramsJson = toJson(params);
        return create(kind, "", key, owner, IdempotencyService.sha256(kind.name() + "|" + paramsJson), paramsJson, null, null, authentication);
    }

    /**
     * Cột và tùy chọn xuất đi kèm query của nút Xuất Excel: {@code columns} (lặp lại, theo tên cột) và các tùy chọn trong
     * {@link #EXPORT_OPTIONS}. Đọc thẳng từ request hiện tại để các controller xuất không phải khai báo thêm tham số;
     * gọi ngoài request (test, tác vụ nội bộ) thì không có lựa chọn nào = xuất mọi cột.
     */
    record ExportSelection(List<String> columns, Map<String, String> options) {
        static ExportSelection fromCurrentRequest() {
            if (!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                    instanceof org.springframework.web.context.request.ServletRequestAttributes attributes)) {
                return new ExportSelection(List.of(), Map.of());
            }
            jakarta.servlet.http.HttpServletRequest http = attributes.getRequest();
            List<String> columns = new java.util.ArrayList<>();
            String[] values = http.getParameterValues("columns");
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank() && !columns.contains(value.trim()) && columns.size() < 100) {
                        columns.add(value.trim());
                    }
                }
            }
            Map<String, String> options = new LinkedHashMap<>();
            for (String name : EXPORT_OPTIONS) {
                String value = http.getParameter(name);
                if (value != null && !value.isBlank()) {
                    options.put(name, value.trim().length() > 64 ? value.trim().substring(0, 64) : value.trim());
                }
            }
            return new ExportSelection(columns, options);
        }
    }

    /** Tên các tùy chọn xuất được nhận từ query (ngoài bộ lọc danh sách). */
    public static final List<String> EXPORT_OPTIONS = List.of("template", "groupBy", "layout");

    public ExcelJobResponse createImport(ExcelJobKind kind, String target, MultipartFile file, String idempotencyKey, Authentication authentication) {
        return createImport(kind, target, file, null, idempotencyKey, authentication);
    }

    public ExcelJobResponse createImport(ExcelJobKind kind, String target, MultipartFile file, MultipartFile images,
                                         String idempotencyKey, Authentication authentication) {
        String key = IdempotencyService.requireValidKey(idempotencyKey);
        String owner = owner(authentication);
        byte[] content = readUpload(file);
        byte[] imageContent = readImages(images);
        String imageHash = imageContent == null ? "" : IdempotencyService.sha256(imageContent);
        String hash = IdempotencyService.sha256(kind.name() + "|" + target + "|" + IdempotencyService.sha256(content) + "|" + imageHash);
        // Lưu file ra đĩa trước (request kết thúc là file upload tạm bị xóa); xóa lại nếu job không được tạo mới.
        Path input = writeFile("inputs", UUID.randomUUID() + ".xlsx", content);
        Path imagesPath = imageContent == null ? null : writeFile("inputs", UUID.randomUUID() + ".zip", imageContent);
        boolean keep = false;
        try {
            ExcelJobResponse response = create(kind, target, key, owner, hash, null, input, imagesPath, authentication);
            keep = !response.replayed();
            return response;
        } finally {
            if (!keep) {
                deleteQuietly(input.toString());
                deleteQuietly(imagesPath == null ? null : imagesPath.toString());
            }
        }
    }

    private ExcelJobResponse create(ExcelJobKind kind, String target, String key, String owner, String hash, String paramsJson,
                                    Path input, Path images, Authentication authentication) {
        // Cùng key: trả lại job cũ (nếu nội dung khác thì từ chối).
        Optional<ExcelJobRepository.Row> existing = repository.findByKey(owner, kind, key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        Optional<ExcelJobRepository.Row> active = repository.findActive(owner, kind, target);
        if (active.isPresent()) {
            // Job đang chạy chính là job của key này (request cùng key đến sau khi job vừa được tạo): trả lại job đó, không phải xung đột.
            if (active.get().idempotencyKey().equals(key)) {
                return replay(active.get(), hash);
            }
            throw activeConflict(active.get());
        }
        String warning = kind.isImport()
                ? repository.findLastSucceeded(owner, kind, target, hash)
                .map(at -> "File này đã được import thành công lúc " + at.atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) + ". Nếu dữ liệu đã có sẵn, lần này sẽ báo trùng.")
                .orElse(null)
                : null;
        JobPrincipal principal = JobPrincipal.of(authentication, owner, securityUtils.currentLeaderId(authentication));
        // Insert nằm trong transaction riêng: nếu DB từ chối (xung đột đồng thời) thì transaction đó bị hủy nhưng ta vẫn tra cứu lại được.
        Optional<UUID> id;
        try {
            String principalJson = toJson(principal);
            String inputPath = input == null ? null : input.toString();
            String imagesPath = images == null ? null : images.toString();
            id = new TransactionTemplate(transactionManager).execute(status ->
                    repository.insert(owner, kind, target, key, hash, paramsJson, principalJson, inputPath, imagesPath, warning));
        } catch (DataIntegrityViolationException exception) {
            // Request cùng key đang tạo job cùng lúc (đụng unique index "một job đang chạy"): trả lại job đó.
            Optional<ExcelJobRepository.Row> sameKey = repository.findByKey(owner, kind, key);
            if (sameKey.isPresent()) {
                return replay(sameKey.get(), hash);
            }
            // Request khác key tạo cùng lúc: chỉ một job đang chạy / chờ cho cùng (loại, đích).
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                    "Đang có một tác vụ cùng loại đang chạy. Vui lòng chờ tác vụ đó hoàn tất rồi thử lại");
        }
        if (id == null || id.isEmpty()) {
            // Một request cùng key vừa commit trong lúc ta chờ: trả lại job đó.
            return replay(repository.findByKey(owner, kind, key).orElseThrow(), hash);
        }
        worker.wake();
        return toResponse(repository.findById(id.get()).orElseThrow()).withReplayed(false);
    }

    /**
     * Xử lý các dòng nghi trùng của một lần import đã xong theo quyết định của người dùng: tạo một job import mới chạy lại đúng file đó,
     * chỉ với các dòng đã có quyết định (cùng một người: không tạo mới; hai người khác nhau: tạo hồ sơ mới). Mỗi lần import chỉ xử lý một lần.
     */
    public ExcelJobResponse resolveDuplicates(UUID id, List<DuplicateDecision> decisions, Authentication authentication) {
        ExcelJobRepository.Row source = requireOwned(id, authentication);
        String key = "resolve-" + id;
        String paramsJson = toJson(Map.of("decisions", decisions));
        String hash = IdempotencyService.sha256("RESOLVE|" + id + "|" + paramsJson);
        Optional<ExcelJobRepository.Row> existing = repository.findByKey(source.owner(), source.kind(), key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        if (!source.kind().isImport() || !"SUCCEEDED".equals(source.status()) || source.pendingInputPath() == null
                || !Files.isRegularFile(Path.of(source.pendingInputPath()))) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                    "Không còn hồ sơ nghi trùng nào cần xác nhận (đã xử lý hoặc đã quá hạn lưu). Vui lòng import lại file nếu cần");
        }
        Set<String> expected = new HashSet<>();
        for (ImportDuplicate duplicate : listDuplicates(source.resultJson())) {
            expected.add(DuplicateDecision.key(duplicate.sheet(), duplicate.row()));
        }
        Set<String> given = new HashSet<>();
        for (DuplicateDecision decision : decisions) {
            if (!expected.contains(decision.key()) || !given.add(decision.key())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                        "Quyết định không khớp với các hồ sơ nghi trùng của lần import này");
            }
        }
        if (!given.equals(expected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Vui lòng xác nhận tất cả " + expected.size() + " hồ sơ nghi trùng trước khi tiếp tục");
        }
        Path input = Path.of(source.pendingInputPath());
        Path images = source.pendingImagesPath() != null && Files.isRegularFile(Path.of(source.pendingImagesPath()))
                ? Path.of(source.pendingImagesPath()) : null;
        ExcelJobResponse response = create(source.kind(), source.target(), key, source.owner(), hash, paramsJson, input, images, authentication);
        if (!response.replayed()) {
            // File được chuyển cho job mới (job đó xóa file khi xong); job cũ không giữ nữa để không xử lý lần hai.
            repository.clearPending(id);
        }
        return response;
    }

    public record DuplicateScope(ExcelJobKind kind, String target) {
    }

    /** Loại và đích của job để controller kiểm tra đúng quyền như lúc tạo import. */
    public DuplicateScope scopeOf(UUID id, Authentication authentication) {
        ExcelJobRepository.Row row = requireOwned(id, authentication);
        return new DuplicateScope(row.kind(), row.target());
    }

    private List<ImportDuplicate> listDuplicates(String resultJson) {
        if (resultJson == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(resultJson);
            return node.has("duplicates")
                    ? objectMapper.convertValue(node.get("duplicates"), new TypeReference<List<ImportDuplicate>>() {
                    })
                    : List.of();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private ExcelJobResponse replay(ExcelJobRepository.Row row, String hash) {
        if (!row.payloadHash().equals(hash)) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                    "Idempotency-Key này đã được dùng cho một yêu cầu khác. Hãy tạo key mới cho yêu cầu mới");
        }
        return toResponse(row).withReplayed(true);
    }

    private ApiException activeConflict(ExcelJobRepository.Row active) {
        return new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                "Đang có một tác vụ \"" + active.kind().label() + "\" đang chạy hoặc chờ xử lý. Vui lòng chờ tác vụ đó hoàn tất rồi thử lại");
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    // ---- xem / hủy / tải ----

    @Transactional(readOnly = true)
    public ExcelJobResponse get(UUID id, Authentication authentication) {
        return toResponse(requireOwned(id, authentication));
    }

    @Transactional(readOnly = true)
    public List<ExcelJobResponse> listMine(Authentication authentication) {
        return repository.listByOwner(owner(authentication), 30).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExcelJobResponse cancel(UUID id, Authentication authentication) {
        ExcelJobRepository.Row row = requireOwned(id, authentication);
        // Import đang chạy chạy trong một transaction duy nhất nên không hủy giữa chừng; xuất hủy được.
        Optional<String> result = repository.requestCancel(id, row.owner(), !row.kind().isImport());
        if (result.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                    row.kind().isImport() && "RUNNING".equals(row.status())
                            ? "Import đang ghi dữ liệu nên không hủy được giữa chừng"
                            : "Tác vụ đã kết thúc nên không hủy được");
        }
        return toResponse(repository.findById(id).orElseThrow());
    }

    public record Download(Path path, String fileName) {
    }

    @Transactional(readOnly = true)
    public Download download(UUID id, Authentication authentication) {
        ExcelJobRepository.Row row = requireOwned(id, authentication);
        if (!"SUCCEEDED".equals(row.status()) || row.outputPath() == null || !Files.isRegularFile(Path.of(row.outputPath()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "File kết quả không còn hoặc chưa sẵn sàng");
        }
        return new Download(Path.of(row.outputPath()), row.outputName() == null ? "ket-qua.xlsx" : row.outputName());
    }

    private ExcelJobRepository.Row requireOwned(UUID id, Authentication authentication) {
        return repository.findById(id)
                .filter(row -> row.owner().equals(owner(authentication)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tác vụ"));
    }

    // ---- dọn dẹp / khôi phục ----

    /** Xóa job + file quá hạn giữ (3 ngày). */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 90_000L)
    @Transactional
    public void purgeExpired() {
        for (ExcelJobRepository.Expired expired : repository.deleteExpired(RETENTION_DAYS)) {
            deleteQuietly(expired.inputPath());
            deleteQuietly(expired.outputPath());
            deleteQuietly(expired.imagesPath());
            deleteQuietly(expired.pendingInputPath());
            deleteQuietly(expired.pendingImagesPath());
        }
    }

    // ---- tiện ích ----

    public Path directory(String sub) {
        try {
            return Files.createDirectories(Path.of(baseDir, sub));
        } catch (IOException exception) {
            throw new UncheckedIOException("Không tạo được thư mục tác vụ Excel", exception);
        }
    }

    private Path writeFile(String sub, String name, byte[] content) {
        try {
            Path path = directory(sub).resolve(name);
            Files.write(path, content);
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("Không lưu được file Excel tạm", exception);
        }
    }

    static void deleteQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException ignored) {
            // file tạm: lần dọn sau sẽ xóa
        }
    }

    private byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Vui lòng chọn file Excel (.xlsx)");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Chỉ hỗ trợ file Excel định dạng .xlsx");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "File Excel vượt quá 20MB");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không đọc được file tải lên", exception);
        }
    }

    private byte[] readImages(MultipartFile images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        String name = images.getOriginalFilename() == null ? "" : images.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".zip")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "File ảnh phải có định dạng .zip");
        }
        if (images.getSize() > MAX_IMAGES_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "File ZIP ảnh vượt quá 50MB");
        }
        try {
            return images.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không đọc được file ZIP ảnh tải lên", exception);
        }
    }

    private String owner(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Không xác định được tài khoản hiện tại");
        }
        return username;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public ExcelJobResponse toResponse(ExcelJobRepository.Row row) {
        List<ImportError> errors = null;
        Integer created = null;
        String resultMessage = null;
        Integer avatarProcessed = null;
        Integer avatarTotal = null;
        Integer avatarSucceeded = null;
        List<AvatarWarning> avatarWarnings = null;
        List<ImportDuplicate> duplicates = null;
        if (row.resultJson() != null) {
            try {
                JsonNode node = objectMapper.readTree(row.resultJson());
                if (node.has("errors")) {
                    errors = objectMapper.convertValue(node.get("errors"), new TypeReference<List<ImportError>>() {
                    });
                }
                // Chỉ trả dòng nghi trùng khi còn xử lý được (file chờ vẫn còn): đã xác nhận hoặc quá hạn thì không hiện nữa.
                if (row.pendingInputPath() != null && node.has("duplicates")) {
                    duplicates = objectMapper.convertValue(node.get("duplicates"), new TypeReference<List<ImportDuplicate>>() {
                    });
                }
                if (node.has("created")) {
                    created = node.get("created").asInt();
                }
                if (node.has("message")) {
                    resultMessage = node.get("message").asText();
                }
                if (node.has("avatarProcessed")) {
                    avatarProcessed = node.get("avatarProcessed").asInt();
                    avatarTotal = node.path("avatarTotal").asInt();
                    avatarSucceeded = node.path("avatarSucceeded").asInt();
                    avatarWarnings = objectMapper.convertValue(node.path("avatarWarnings"), new TypeReference<List<AvatarWarning>>() {
                    });
                }
            } catch (JsonProcessingException ignored) {
                // kết quả không đọc được: bỏ qua phần chi tiết
            }
        }
        boolean downloadable = "SUCCEEDED".equals(row.status()) && row.outputPath() != null && Files.isRegularFile(Path.of(row.outputPath()));
        return new ExcelJobResponse(row.id(), row.kind(), row.kind().label(), row.kind().isImport(), row.target().isEmpty() ? null : row.target(),
                row.status(), row.processed(), row.total(), row.avatarStatus(), avatarProcessed, avatarTotal, avatarSucceeded, avatarWarnings,
                row.warning(), row.errorMessage(), errors, duplicates, created, resultMessage, downloadable,
                downloadable ? row.outputName() : null, row.createdAt(), row.finishedAt(),
                row.createdAt() == null ? null : row.createdAt().plusDays(RETENTION_DAYS), false);
    }

}
