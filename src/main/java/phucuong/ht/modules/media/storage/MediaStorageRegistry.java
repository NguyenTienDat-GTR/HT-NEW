package phucuong.ht.modules.media.storage;

import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.config.MediaProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh sách nhà cung cấp lưu trữ đang được cấu hình. Ảnh luôn được đọc theo provider ghi trong media_asset,
 * còn ảnh mới ghi vào provider "active". Nhờ vậy có thể cấu hình song song provider cũ và mới khi chuyển dữ liệu.
 */
@Component
public class MediaStorageRegistry {

    private final Map<String, MediaStorageProvider> providers = new LinkedHashMap<>();
    private final String activeName;

    public MediaStorageRegistry(List<MediaStorageProvider> available, MediaProperties properties) {
        for (MediaStorageProvider provider : available) {
            providers.put(provider.name(), provider);
        }
        // Không chỉ định provider: có CLOUDINARY_URL thì dùng Cloudinary, ngược lại mới dùng thư mục cục bộ (chỉ để phát triển).
        String configured = properties.getActiveProvider();
        this.activeName = configured == null || configured.isBlank()
                ? (providers.containsKey("cloudinary") ? "cloudinary" : "local")
                : configured;
        if (!providers.containsKey(activeName)) {
            throw new IllegalStateException("app.media.active-provider=" + activeName
                    + " nhưng chưa cấu hình provider này (đang có: " + providers.keySet() + ")");
        }
    }

    public MediaStorageProvider active() {
        return providers.get(activeName);
    }

    public String activeName() {
        return activeName;
    }

    public MediaStorageProvider require(String name) {
        MediaStorageProvider provider = providers.get(name);
        if (provider == null) {
            throw new MediaStorageException("Chưa cấu hình nhà cung cấp lưu trữ '" + name + "'", false);
        }
        return provider;
    }

    public boolean has(String name) {
        return providers.containsKey(name);
    }
}
