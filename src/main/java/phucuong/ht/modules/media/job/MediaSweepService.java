package phucuong.ht.modules.media.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.storage.MediaStorageException;
import phucuong.ht.modules.media.storage.MediaStorageProvider;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lệnh quét dọn chạy tay (một lần hoặc khi cần) cho nhà cung cấp đang dùng, xử lý những gì job hằng giờ không thấy
 * vì job chỉ duyệt các dòng trong {@code media_asset}:
 * <ul>
 *   <li>Tệp nằm dưới {@code {root-prefix}/pending/} không còn dòng {@code media_asset} nào (và đã quá hạn tải lên) → xóa.</li>
 *   <li>Thư mục {@code {assetId}} trống: tên thư mục là UUID của ảnh nhưng không còn dòng tương ứng → xóa
 *       (chỉ thư mục cấp ảnh; thư mục cấu trúc như giáo phận / loại ảnh luôn được giữ).</li>
 * </ul>
 * Mặc định chỉ thống kê (dryRun). Việc duyệt thư mục tốn lượt gọi Admin API của Cloudinary (có hạn mức theo giờ) nên
 * mỗi lần chạy có ngân sách {@code maxFolderCalls}; hết ngân sách thì báo {@code truncated} và chạy lại sau.
 */
@Service
public class MediaSweepService {

    private static final Logger log = LoggerFactory.getLogger(MediaSweepService.class);

    public static final int DEFAULT_MAX_FOLDER_CALLS = 300;
    public static final int MAX_FOLDER_CALLS_LIMIT = 2000;
    private static final int MAX_FILES_PER_RUN = 2000;
    private static final int SAMPLE_SIZE = 30;

    private final MediaStorageRegistry registry;
    private final MediaProperties properties;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MediaSweepService(MediaStorageRegistry registry, MediaProperties properties, JdbcTemplate jdbc) {
        this.registry = registry;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public record SweepReport(
            boolean dryRun,
            String provider,
            int orphanFilesFound,
            int orphanFilesDeleted,
            int fileErrors,
            int emptyFoldersFound,
            int emptyFoldersDeleted,
            int folderApiCalls,
            boolean truncated,
            List<String> sampleFiles,
            List<String> sampleFolders) {
    }

    /** Một lần chạy tại một thời điểm; gọi chồng thì ném {@link IllegalStateException}. */
    public SweepReport sweep(boolean dryRun, int maxFolderCalls) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Lệnh quét dọn đang chạy, vui lòng đợi nó kết thúc");
        }
        try {
            return doSweep(dryRun, Math.max(1, Math.min(maxFolderCalls, MAX_FOLDER_CALLS_LIMIT)));
        } finally {
            running.set(false);
        }
    }

    private SweepReport doSweep(boolean dryRun, int maxFolderCalls) {
        MediaStorageProvider provider = registry.active();
        if (!provider.supportsSweep()) {
            throw new MediaStorageException("Nhà cung cấp " + provider.name() + " không hỗ trợ quét dọn", false);
        }
        String root = properties.getRootPrefix();
        Set<UUID> knownIds = new HashSet<>(jdbc.queryForList("select id from media_asset", UUID.class));
        OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.getCleanup().getPendingTtl());

        // 1) Tệp mồ côi trong thư mục tạm.
        List<String> orphanFiles = new ArrayList<>();
        provider.forEachFile(root + "/pending/", file -> {
            if (file.createdAt().isBefore(cutoff) && !knownIds.contains(assetIdOf(file.key()))) {
                orphanFiles.add(file.key());
            }
            return orphanFiles.size() < MAX_FILES_PER_RUN;
        });
        int deletedFiles = 0;
        int fileErrors = 0;
        if (!dryRun) {
            for (String key : orphanFiles) {
                try {
                    provider.delete(key);
                    deletedFiles++;
                } catch (RuntimeException e) {
                    fileErrors++;
                    log.warn("Không xóa được tệp mồ côi {}: {}", key, e.getMessage());
                }
            }
        }

        // 2) Thư mục {assetId} trống.
        FolderPass pass = new FolderPass(provider, knownIds, dryRun, maxFolderCalls);
        pass.walk(root);

        SweepReport report = new SweepReport(dryRun, provider.name(),
                orphanFiles.size(), deletedFiles, fileErrors,
                pass.found, pass.deleted, pass.calls, pass.truncated,
                orphanFiles.stream().limit(SAMPLE_SIZE).toList(),
                pass.sample.stream().limit(SAMPLE_SIZE).toList());
        log.info("Quét dọn ảnh ({}): tệp mồ côi {} (xóa {}), thư mục trống {} (xóa {}), gọi API thư mục {}{}",
                dryRun ? "chỉ thống kê" : "thực hiện", report.orphanFilesFound(), report.orphanFilesDeleted(),
                report.emptyFoldersFound(), report.emptyFoldersDeleted(), report.folderApiCalls(),
                report.truncated() ? " — hết ngân sách, chạy lại để làm tiếp" : "");
        return report;
    }

    /** Duyệt cây thư mục; chỉ thử xóa thư mục tên UUID không còn dòng media_asset. */
    private static final class FolderPass {
        final MediaStorageProvider provider;
        final Set<UUID> knownIds;
        final boolean dryRun;
        final int budget;
        int calls;
        int found;
        int deleted;
        boolean truncated;
        final List<String> sample = new ArrayList<>();

        FolderPass(MediaStorageProvider provider, Set<UUID> knownIds, boolean dryRun, int budget) {
            this.provider = provider;
            this.knownIds = knownIds;
            this.dryRun = dryRun;
            this.budget = budget;
        }

        boolean spend() {
            if (calls >= budget) {
                truncated = true;
                return false;
            }
            calls++;
            return true;
        }

        void walk(String path) {
            if (!spend()) {
                return;
            }
            List<String> children;
            try {
                children = provider.listFolders(path);
            } catch (MediaStorageException e) {
                log.debug("Không liệt kê được thư mục {}: {}", path, e.getMessage());
                return;
            }
            for (String name : children) {
                if (truncated) {
                    return;
                }
                String child = path + "/" + name;
                UUID id = parseUuid(name);
                if (id == null) {
                    walk(child);
                } else if (!knownIds.contains(id)) {
                    found++;
                    if (sample.size() < SAMPLE_SIZE) {
                        sample.add(child);
                    }
                    if (!dryRun) {
                        if (!spend()) {
                            return;
                        }
                        if (provider.deleteFolder(child)) {
                            deleted++;
                        }
                    }
                }
            }
        }
    }

    /** UUID của ảnh lấy từ tên thư mục chứa tệp: ".../{assetId}/thumb.jpg"; null nếu không đúng dạng. */
    static UUID assetIdOf(String key) {
        int last = key.lastIndexOf('/');
        if (last <= 0) {
            return null;
        }
        int prev = key.lastIndexOf('/', last - 1);
        return parseUuid(key.substring(prev + 1, last));
    }

    static UUID parseUuid(String text) {
        if (text == null || text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
