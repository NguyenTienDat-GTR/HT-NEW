package phucuong.ht.modules.media.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý thư mục lưu ảnh của từng giáo phận: {@code diocese.storage_folder} = slug tên + 8 ký tự id,
 * được tạo khi tạo giáo phận (hoặc lười khi cần) và không đổi dù giáo phận đổi tên.
 */
@Service
public class MediaFolderService {

    private static final Logger log = LoggerFactory.getLogger(MediaFolderService.class);

    private final JdbcTemplate jdbc;
    private final MediaProperties properties;
    private final MediaStorageRegistry registry;
    private final Map<UUID, String> folderCache = new ConcurrentHashMap<>();

    public MediaFolderService(JdbcTemplate jdbc, MediaProperties properties, MediaStorageRegistry registry) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.registry = registry;
    }

    /** Tên thư mục giáo phận (tạo và lưu nếu chưa có). */
    public String dioceseFolder(UUID dioceseId) {
        String cached = folderCache.get(dioceseId);
        if (cached != null) {
            return cached;
        }
        try {
            Map<String, Object> row = jdbc.queryForMap("select name, storage_folder from diocese where id = ?", dioceseId);
            String folder = (String) row.get("storage_folder");
            if (folder == null || folder.isBlank()) {
                folder = MediaFolderNamer.dioceseFolder((String) row.get("name"), dioceseId);
                jdbc.update("update diocese set storage_folder = ? where id = ? and storage_folder is null", folder, dioceseId);
                folder = jdbc.queryForObject("select storage_folder from diocese where id = ?", String.class, dioceseId);
            }
            folderCache.put(dioceseId, folder);
            return folder;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Không tìm thấy giáo phận " + dioceseId);
        }
    }

    /**
     * Giáo phận sở hữu của một cấu hình theo scopeKey: SYSTEM → null (thư mục system),
     * DIOCESE:{id} → id, DEANERY:{id} → giáo phận chủ quản của giáo hạt.
     */
    public UUID dioceseOfScopeKey(String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank() || "SYSTEM".equals(scopeKey)) {
            return null;
        }
        int colon = scopeKey.indexOf(':');
        if (colon < 0) {
            return null;
        }
        UUID id = UUID.fromString(scopeKey.substring(colon + 1));
        if (scopeKey.startsWith("DEANERY:")) {
            return jdbc.queryForObject("select diocese_id from deanery where id = ?", UUID.class, id);
        }
        return id;
    }

    /** Tiền tố khóa của một phạm vi: dioceses/{folder} hoặc system. */
    public String scopeFolder(UUID dioceseId) {
        return dioceseId == null ? "system" : "dioceses/" + dioceseFolder(dioceseId);
    }

    public String baseKey(UUID dioceseId, MediaCategory category, UUID assetId) {
        return properties.getRootPrefix() + "/" + scopeFolder(dioceseId) + "/" + category.getFolder() + "/" + assetId;
    }

    public String pendingBaseKey(MediaCategory category, UUID assetId) {
        return properties.getRootPrefix() + "/pending/" + category.getFolder() + "/" + assetId;
    }

    /** Tạo sẵn thư mục cho giáo phận mới trên cloud (lỗi chỉ ghi log, không chặn việc tạo giáo phận). */
    public void provisionDiocese(UUID dioceseId) {
        try {
            String prefix = properties.getRootPrefix() + "/" + scopeFolder(dioceseId);
            for (MediaCategory category : MediaCategory.values()) {
                registry.active().ensureFolder(prefix + "/" + category.getFolder());
            }
        } catch (RuntimeException e) {
            log.warn("Không tạo được thư mục ảnh cho giáo phận {}: {}", dioceseId, e.getMessage());
        }
    }
}
