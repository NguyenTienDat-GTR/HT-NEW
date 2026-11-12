package phucuong.ht.modules.media.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.config.MediaProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Lưu ảnh trên Cloudinary với type=authenticated: không có URL công khai, chỉ truy cập được bằng URL đã ký
 * do chính backend tạo ra. Frontend không bao giờ nhận URL của Cloudinary.
 * Khóa logic "a/b/c.jpg" tương ứng public_id "a/b/c" (định dạng lấy từ phần mở rộng).
 */
@Component
@ConditionalOnExpression("!'${app.media.cloudinary.url:}'.isBlank()")
public class CloudinaryStorageProvider implements MediaStorageProvider {

    public static final String NAME = "cloudinary";
    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageProvider.class);
    private static final String DELIVERY_TYPE = "authenticated";

    private final Cloudinary cloudinary;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CloudinaryStorageProvider(MediaProperties properties) {
        this.cloudinary = new Cloudinary(properties.getCloudinary().getUrl());
        this.cloudinary.config.secure = true;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Map<String, Object> options = new HashMap<>(ObjectUtils.asMap(
                "public_id", publicId(key),
                "resource_type", "image",
                "type", DELIVERY_TYPE,
                "overwrite", true,
                "invalidate", true,
                "unique_filename", false,
                "use_filename", false));
        // Tài khoản Cloudinary "dynamic folder mode": thư mục hiển thị trong Media Library là asset_folder, không suy từ public_id
        // (thiếu tham số này thì mọi ảnh nằm ở thư mục gốc "Home" dù public_id có dấu "/").
        String folder = folderOf(key);
        if (folder != null) {
            options.put("asset_folder", folder);
        }
        try {
            cloudinary.uploader().upload(data, options);
        } catch (IOException | RuntimeException e) {
            if (folder != null && String.valueOf(e.getMessage()).contains("asset_folder")) {
                // Tài khoản fixed folder mode không nhận asset_folder: thư mục đã nằm trong public_id.
                options.remove("asset_folder");
                try {
                    cloudinary.uploader().upload(data, options);
                    return;
                } catch (IOException | RuntimeException retry) {
                    throw new MediaStorageException("Không tải được ảnh lên Cloudinary", retry);
                }
            }
            throw new MediaStorageException("Không tải được ảnh lên Cloudinary", e);
        }
    }

    @Override
    public byte[] get(String key) {
        String url = cloudinary.url()
                .resourceType("image")
                .type(DELIVERY_TYPE)
                .signed(true)
                .format(extension(key))
                .generate(publicId(key));
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new MediaStorageException("Không tìm thấy ảnh: " + key, true);
            }
            if (response.statusCode() / 100 != 2) {
                throw new MediaStorageException("Cloudinary trả về mã " + response.statusCode() + " khi tải ảnh", false);
            }
            return response.body();
        } catch (IOException e) {
            throw new MediaStorageException("Không đọc được ảnh từ Cloudinary", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaStorageException("Bị ngắt khi đọc ảnh từ Cloudinary", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            cloudinary.uploader().destroy(publicId(key), ObjectUtils.asMap(
                    "resource_type", "image",
                    "type", DELIVERY_TYPE,
                    "invalidate", true));
        } catch (IOException | RuntimeException e) {
            throw new MediaStorageException("Không xóa được ảnh trên Cloudinary", e);
        }
    }

    @Override
    public void move(String sourceKey, String targetKey, String contentType) {
        try {
            cloudinary.uploader().rename(publicId(sourceKey), publicId(targetKey), ObjectUtils.asMap(
                    "resource_type", "image",
                    "type", DELIVERY_TYPE,
                    "to_type", DELIVERY_TYPE,
                    "overwrite", true,
                    "invalidate", true));
        } catch (IOException | RuntimeException e) {
            throw new MediaStorageException("Không đổi được vị trí ảnh trên Cloudinary", e);
        }
        syncFolder(targetKey);
    }

    /** Đưa asset vào thư mục hiển thị tương ứng với khóa (dynamic folder mode); lỗi chỉ ghi log vì ảnh vẫn dùng được. */
    @Override
    public void syncFolder(String key) {
        String folder = folderOf(key);
        if (folder == null) {
            return;
        }
        try {
            cloudinary.api().update(publicId(key), ObjectUtils.asMap(
                    "resource_type", "image",
                    "type", DELIVERY_TYPE,
                    "asset_folder", folder));
        } catch (Exception e) {
            log.warn("Không đặt được thư mục hiển thị {} cho ảnh {}: {}", folder, key, e.getMessage());
        }
    }

    @Override
    public void ensureFolder(String prefix) {
        try {
            cloudinary.api().createFolder(prefix, Map.of());
        } catch (Exception e) {
            // Thư mục tự được tạo khi tải ảnh lên; việc tạo trước chỉ để người quản trị nhìn thấy cấu trúc.
            log.debug("Không tạo sẵn được thư mục Cloudinary {}: {}", prefix, e.getMessage());
        }
    }

    /** Thư mục chứa khóa: "a/b/c.jpg" → "a/b"; null nếu khóa không có thư mục. */
    static String folderOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash > 0 ? key.substring(0, slash) : null;
    }

    static String publicId(String key) {
        int dot = key.lastIndexOf('.');
        return dot > key.lastIndexOf('/') ? key.substring(0, dot) : key;
    }

    static String extension(String key) {
        int dot = key.lastIndexOf('.');
        return dot > key.lastIndexOf('/') ? key.substring(dot + 1) : "jpg";
    }
}
