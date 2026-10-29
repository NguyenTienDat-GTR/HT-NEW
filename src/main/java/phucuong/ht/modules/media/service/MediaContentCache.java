package phucuong.ht.modules.media.service;

import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.config.MediaProperties;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ nhớ đệm LRU trong tiến trình cho các biến thể nhỏ (thumb/medium/preview) để danh sách nhiều avatar
 * không phải gọi sang nhà cung cấp cloud cho từng ảnh. Nội dung của (asset, biến thể) không bao giờ đổi
 * nên không cần vô hiệu hóa; chỉ giới hạn tổng dung lượng.
 */
@Component
public class MediaContentCache {

    private final long maxBytes;
    private final long maxEntryBytes;
    private final LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>(256, 0.75f, true);
    private long currentBytes;

    public MediaContentCache(MediaProperties properties) {
        this.maxBytes = properties.getCacheMaxBytes();
        this.maxEntryBytes = properties.getCacheMaxEntryBytes();
    }

    public synchronized byte[] get(String key) {
        return entries.get(key);
    }

    public synchronized void put(String key, byte[] data) {
        if (data == null || data.length > maxEntryBytes || data.length > maxBytes) {
            return;
        }
        byte[] previous = entries.put(key, data);
        if (previous != null) {
            currentBytes -= previous.length;
        }
        currentBytes += data.length;
        Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator();
        while (currentBytes > maxBytes && it.hasNext()) {
            Map.Entry<String, byte[]> eldest = it.next();
            currentBytes -= eldest.getValue().length;
            it.remove();
        }
    }

    public synchronized void evict(String key) {
        byte[] removed = entries.remove(key);
        if (removed != null) {
            currentBytes -= removed.length;
        }
    }
}
