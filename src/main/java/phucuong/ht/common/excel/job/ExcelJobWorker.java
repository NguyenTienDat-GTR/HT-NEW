package phucuong.ht.common.excel.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.ImportValidationException;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.config.CacheNames;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.training.CourseScoreVisibility;
import phucuong.ht.modules.media.config.MediaProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Chạy tác vụ Excel nền. Mỗi node nhận job bằng {@code UPDATE ... FOR UPDATE SKIP LOCKED} nên không bao giờ hai worker cùng chạy một job.
 *
 * <ul>
 *   <li>Nhập chạy trong MỘT transaction và đánh dấu SUCCEEDED trong chính transaction đó: dữ liệu và trạng thái job cùng commit hoặc cùng rollback,
 *       nên job bị gián đoạn rồi chạy lại là an toàn (không có trường hợp "đã ghi dữ liệu nhưng job báo lỗi").</li>
 *   <li>Trùng dữ liệu do thao tác khác cùng lúc (DB từ chối bằng ràng buộc duy nhất): rollback cả job và chạy lại một lần; lần chạy lại kiểm tra
 *       lại toàn file nên chỉ ra chính xác dòng trùng.</li>
 *   <li>Lease + heartbeat: job đang chạy mà worker chết thì sau khi hết lease được đưa lại hàng đợi (tối đa {@value #MAX_ATTEMPTS} lượt).</li>
 * </ul>
 */
@Component
public class ExcelJobWorker {

    private static final Logger log = LoggerFactory.getLogger(ExcelJobWorker.class);
    static final int WORKERS = 2;
    static final int LEASE_SECONDS = 300;
    static final int MAX_ATTEMPTS = 3;

    private static final String[] CACHES_AFTER_IMPORT = {
            CacheNames.DIOCESE_LIST, CacheNames.DIOCESE_DETAIL, CacheNames.DEANERY_LIST, CacheNames.DEANERY_DETAIL,
            CacheNames.PARISH_LIST, CacheNames.PARISH_DETAIL, CacheNames.LEADER_LIST, CacheNames.LEADER_DETAIL,
            CacheNames.TRAINING_COURSE_LIST, CacheNames.TRAINING_COURSE_DETAIL, CacheNames.CERTIFICATE_LIST, CacheNames.CERTIFICATE_DETAIL,
            CacheNames.ANALYTICS_OVERVIEW, CacheNames.ANALYTICS_ORGANIZATION, CacheNames.ANALYTICS_LEADERS, CacheNames.ANALYTICS_COURSES,
            CacheNames.ANALYTICS_CERTIFICATES
    };

    private final ExcelJobRepository repository;
    private final ExcelJobHandlers handlers;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final ExcelAvatarProcessor avatarProcessor;
    private final MediaProperties mediaProperties;
    private final TransactionTemplate runTx;
    private final TransactionTemplate shortTx;
    private final Semaphore permits = new Semaphore(WORKERS);
    private final ExecutorService jobExecutor = Executors.newFixedThreadPool(WORKERS, runnable -> {
        Thread thread = new Thread(runnable, "excel-job");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService wakeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "excel-job-wake");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${app.excel-jobs.dir:${java.io.tmpdir}/ht-excel-jobs}")
    private String baseDir;

    public ExcelJobWorker(ExcelJobRepository repository, ExcelJobHandlers handlers, ObjectMapper objectMapper,
                          CacheManager cacheManager, PlatformTransactionManager transactionManager,
                          ExcelAvatarProcessor avatarProcessor, MediaProperties mediaProperties) {
        this.repository = repository;
        this.handlers = handlers;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
        this.avatarProcessor = avatarProcessor;
        this.mediaProperties = mediaProperties;
        this.runTx = new TransactionTemplate(transactionManager);
        // Ghi trạng thái / tiến độ luôn ở transaction riêng, độc lập với transaction nhập dữ liệu đang mở.
        this.shortTx = new TransactionTemplate(transactionManager);
        this.shortTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PreDestroy
    void shutdown() {
        jobExecutor.shutdownNow();
        wakeExecutor.shutdownNow();
    }

    /** Gọi ngay sau khi tạo job để job chạy tức thì thay vì đợi vòng quét kế tiếp. */
    public void wake() {
        wakeExecutor.execute(this::poll);
    }

    @Scheduled(fixedDelay = 2000L, initialDelay = 5000L)
    public void scheduledPoll() {
        try {
            shortTx.executeWithoutResult(status -> repository.recoverStale(MAX_ATTEMPTS));
        } catch (RuntimeException exception) {
            log.warn("Không khôi phục được job Excel quá hạn lease", exception);
        }
        pollAvatars();
        poll();
    }

    private void pollAvatars() {
        while (permits.tryAcquire()) {
            Optional<ExcelJobRepository.Row> claimed;
            try {
                claimed = shortTx.execute(status -> repository.claimNextAvatar(LEASE_SECONDS));
            } catch (RuntimeException exception) {
                permits.release();
                log.warn("Không nhận được job xử lý ảnh Excel", exception);
                return;
            }
            if (claimed == null || claimed.isEmpty()) {
                permits.release();
                return;
            }
            ExcelJobRepository.Row row = claimed.get();
            jobExecutor.execute(() -> {
                try {
                    avatarProcessor.process(row, LEASE_SECONDS);
                } catch (RuntimeException exception) {
                    // Giữ RUNNING; khi hết lease vòng quét sẽ nhận lại.
                    log.error("Xử lý ảnh của job Excel {} bị gián đoạn", row.id(), exception);
                } finally {
                    permits.release();
                }
            });
        }
    }

    void poll() {
        while (permits.tryAcquire()) {
            Optional<ExcelJobRepository.Row> claimed;
            try {
                claimed = shortTx.execute(status -> repository.claimNext(LEASE_SECONDS));
            } catch (RuntimeException exception) {
                permits.release();
                log.warn("Không nhận được job Excel", exception);
                return;
            }
            if (claimed == null || claimed.isEmpty()) {
                permits.release();
                return;
            }
            ExcelJobRepository.Row row = claimed.get();
            jobExecutor.execute(() -> {
                try {
                    run(row);
                } finally {
                    permits.release();
                }
            });
        }
    }

    // ---- chạy một job ----

    private void run(ExcelJobRepository.Row row) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            Authentication authentication = objectMapper.readValue(row.principalJson(), JobPrincipal.class).toAuthentication();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            if (row.kind().isImport()) {
                runImport(row, authentication);
            } else {
                runExport(row, authentication);
            }
        } catch (JsonProcessingException exception) {
            fail(row, "Không đọc được thông tin người tạo tác vụ", null);
        } finally {
            CourseScoreVisibility.clearThreadViewer();
            SecurityContextHolder.setContext(previous);
            SecurityContextHolder.clearContext();
        }
    }

    private void runImport(ExcelJobRepository.Row row, Authentication authentication) {
        boolean requeued = false;
        boolean deleteImages = false;
        AvatarZip images = null;
        try {
            if (row.imagesPath() != null) {
                try {
                    images = new AvatarZip(Path.of(row.imagesPath()), mediaProperties.getMaxAvatarBytes());
                } catch (IOException exception) {
                    images = AvatarZip.unavailable(mediaProperties.getMaxAvatarBytes(), "Không đọc được ZIP ảnh: " + exception.getMessage());
                }
            }
            AvatarZip importImages = images;
            JobProgress progress = (processed, total) -> shortTx.executeWithoutResult(status -> repository.touch(row.id(), processed, total, LEASE_SECONDS));
            ImportSummary summary = runTx.execute(status -> {
                ImportSummary result = handlers.runImport(row.kind(), row.target(), Path.of(row.inputPath()), authentication, progress, importImages);
                boolean avatarRequested = row.imagesPath() != null || !result.avatarPlan().isEmpty() || !result.avatarWarnings().isEmpty();
                String avatarStatus = avatarRequested ? (result.avatarPlan().isEmpty() ? "DONE" : "PENDING") : null;
                Map<String, Object> resultJson = new LinkedHashMap<>();
                resultJson.put("created", result.created());
                resultJson.put("message", result.message());
                if (avatarRequested) {
                    resultJson.put("avatarProcessed", 0);
                    resultJson.put("avatarTotal", result.avatarPlan().size());
                    resultJson.put("avatarSucceeded", 0);
                    resultJson.put("avatarWarnings", result.avatarWarnings());
                }
                int updated = repository.markSucceeded(row.id(), json(resultJson), null, null, result.created(), result.created(),
                        result.avatarPlan().isEmpty() ? null : json(result.avatarPlan()), avatarStatus);
                if (updated != 1) {
                    // Job không còn ở trạng thái RUNNING (bị thay đổi từ nơi khác): hủy ghi dữ liệu.
                    throw new IllegalStateException("Trạng thái tác vụ đã thay đổi, hủy ghi dữ liệu");
                }
                return result;
            });
            log.info("Job nhập Excel {} hoàn tất: {} dòng", row.id(), summary == null ? 0 : summary.created());
            evictCachesAfterImport();
            if (summary != null && summary.avatarPlan().isEmpty()) {
                ExcelJobService.deleteQuietly(row.imagesPath());
            } else {
                pollAvatars();
            }
        } catch (ImportValidationException exception) {
            fail(row, exception.getMessage(), json(Map.of("errors", exception.getErrors())));
            deleteImages = true;
        } catch (DataIntegrityViolationException exception) {
            // Có thao tác khác vừa tạo dữ liệu trùng (ràng buộc duy nhất của DB chặn). Rollback đã xảy ra; chạy lại một lần để kiểm tra lại
            // toàn file và báo đúng dòng trùng.
            if (row.attempts() < 2) {
                log.info("Job nhập Excel {} gặp xung đột dữ liệu khi ghi, chạy lại", row.id());
                shortTx.executeWithoutResult(status -> repository.requeue(row.id()));
                requeued = true;
            } else {
                fail(row, "Dữ liệu bị trùng do có thao tác khác diễn ra cùng lúc. Vui lòng thử lại", null);
                deleteImages = true;
            }
        } catch (ApiException exception) {
            fail(row, exception.getMessage(), null);
            deleteImages = true;
        } catch (RuntimeException exception) {
            log.error("Job nhập Excel {} thất bại", row.id(), exception);
            fail(row, "Lỗi hệ thống khi xử lý file. Vui lòng thử lại hoặc liên hệ quản trị viên", null);
            deleteImages = true;
        } finally {
            if (images != null) {
                try {
                    images.close();
                } catch (IOException ignored) {
                    // file tạm sẽ được dọn sau
                }
            }
            if (!requeued) {
                ExcelJobService.deleteQuietly(row.inputPath());
            }
            if (deleteImages) {
                ExcelJobService.deleteQuietly(row.imagesPath());
            }
        }
    }

    private void runExport(ExcelJobRepository.Row row, Authentication authentication) {
        Path output = null;
        try {
            BaseSearchRequest request = requestOf(row.paramsJson());
            JobProgress progress = (processed, total) -> {
                Boolean cancel = shortTx.execute(status -> repository.touch(row.id(), processed, total, LEASE_SECONDS));
                if (Boolean.TRUE.equals(cancel)) {
                    throw new JobProgress.JobCancelledException();
                }
            };
            byte[] content = handlers.export(row.kind(), request, authentication, progress);
            Path dir = Files.createDirectories(Path.of(baseDir, "outputs"));
            output = dir.resolve(row.id() + ".xlsx");
            Files.write(output, content);
            String name = handlers.exportFileBase(row.kind(), request) + "-" + LocalDate.now() + ".xlsx";
            Path written = output;
            Integer updated = shortTx.execute(status -> repository.markSucceeded(row.id(),
                    json(Map.of("message", "Đã xuất file " + name)), written.toString(), name, null, null, null, null));
            if (updated == null || updated != 1) {
                Files.deleteIfExists(output);
            }
        } catch (JobProgress.JobCancelledException exception) {
            shortTx.executeWithoutResult(status -> repository.markCancelled(row.id()));
            ExcelJobService.deleteQuietly(output == null ? null : output.toString());
        } catch (ApiException exception) {
            fail(row, exception.getMessage(), null);
        } catch (IOException exception) {
            log.error("Job xuất Excel {} không ghi được file", row.id(), exception);
            fail(row, "Không ghi được file kết quả", null);
        } catch (RuntimeException exception) {
            log.error("Job xuất Excel {} thất bại", row.id(), exception);
            fail(row, "Lỗi hệ thống khi xuất file. Vui lòng thử lại hoặc liên hệ quản trị viên", null);
        }
    }

    private void fail(ExcelJobRepository.Row row, String message, String resultJson) {
        try {
            shortTx.executeWithoutResult(status -> repository.markFailed(row.id(), message, resultJson));
        } catch (RuntimeException exception) {
            log.error("Không ghi được trạng thái lỗi của job Excel {}", row.id(), exception);
        }
    }

    private BaseSearchRequest requestOf(String paramsJson) {
        try {
            Map<String, Object> params = paramsJson == null ? Map.of() : objectMapper.readValue(paramsJson, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
            });
            BaseSearchRequest request = new BaseSearchRequest();
            request.setSearch((String) params.get("search"));
            Object searchFields = params.get("searchFields");
            request.setSearchFields(searchFields instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of());
            Object filters = params.get("filters");
            if (filters instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, value) -> converted.put(String.valueOf(key), value));
                request.setFilters(converted);
            }
            Object status = params.get("status");
            request.setStatus(status instanceof Boolean flag ? flag : null);
            request.setSortBy((String) params.get("sortBy"));
            if (params.get("sortDirection") != null) {
                request.setSortDirection(String.valueOf(params.get("sortDirection")));
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // Xóa cache SAU khi commit (annotation @CacheEvict của service chạy trước commit nên request khác có thể nạp lại dữ liệu cũ).
    private void evictCachesAfterImport() {
        for (String name : CACHES_AFTER_IMPORT) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
