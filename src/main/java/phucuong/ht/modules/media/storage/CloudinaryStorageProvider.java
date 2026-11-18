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

    /** File thường (mẫu Excel...): tài nguyên "raw", public_id giữ nguyên phần mở rộng; cùng khóa thì ghi đè (overwrite + invalidate). */
    @Override
    public void putFile(String key, byte[] data, String contentType) {
        Map<String, Object> options = new HashMap<>(ObjectUtils.asMap(
                "public_id", key,
                "resource_type", "raw",
                "type", DELIVERY_TYPE,
                "overwrite", true,
                "invalidate", true,
                "unique_filename", false,
                "use_filename", false));
        String folder = folderOf(key);
        if (folder != null) {
            options.put("asset_folder", folder);
        }
        try {
            cloudinary.uploader().upload(data, options);
        } catch (IOException | RuntimeException e) {
            if (folder != null && String.valueOf(e.getMessage()).contains("asset_folder")) {
                options.remove("asset_folder");
                try {
                    cloudinary.uploader().upload(data, options);
                    return;
                } catch (IOException | RuntimeException retry) {
                    throw new MediaStorageException("Không tải được file lên Cloudinary", retry);
                }
            }
            throw new MediaStorageException("Không tải được file lên Cloudinary", e);
        }
    }

    @Override
    public byte[] getFile(String key) {
        String url = cloudinary.url().resourceType("raw").type(DELIVERY_TYPE).signed(true).generate(key);
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new MediaStorageException("Không tìm thấy file: " + key, true);
            }
            if (response.statusCode() / 100 != 2) {
                throw new MediaStorageException("Cloudinary trả về mã " + response.statusCode() + " khi tải file", false);
            }
            return response.body();
        } catch (IOException e) {
            throw new MediaStorageException("Không đọc được file từ Cloudinary", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaStorageException("Bị ngắt khi đọc file từ Cloudinary", e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            cloudinary.uploader().destroy(key, ObjectUtils.asMap("resource_type", "raw", "type", DELIVERY_TYPE, "invalidate", true));
        } catch (IOException | RuntimeException e) {
            throw new MediaStorageException("Không xóa được file trên Cloudinary", e);
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

    /**
     * Cloudinary không tự xóa thư mục khi tệp bên trong bị xóa hoặc chuyển đi. API chỉ xóa được thư mục trống
     * (thư mục chỉ chứa thư mục con trống cũng được), nên thư mục còn tệp bị từ chối và ta chỉ ghi log mức debug.
     */
    @Override
    public boolean deleteFolder(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            cloudinary.api().deleteFolder(path, Map.of());
            return true;
        } catch (Exception e) {
            log.debug("Chưa xóa được thư mục Cloudinary {}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean supportsSweep() {
        return true;
    }

    @Override
    public void forEachFile(String prefix, java.util.function.Predicate<StoredFile> visitor) {
        String cursor = null;
        try {
            do {
                Map<String, Object> options = new HashMap<>(ObjectUtils.asMap(
                        "resource_type", "image",
                        "type", DELIVERY_TYPE,
                        "prefix", prefix,
                        "max_results", 500));
                if (cursor != null) {
                    options.put("next_cursor", cursor);
                }
                Map<?, ?> response = cloudinary.api().resources(options);
                Object resources = response.get("resources");
                if (resources instanceof java.util.List<?> list) {
                    for (Object item : list) {
                        Map<?, ?> resource = (Map<?, ?>) item;
                        String publicId = String.valueOf(resource.get("public_id"));
                        Object format = resource.get("format");
                        String key = publicId + "." + (format == null ? "jpg" : format);
                        Object created = resource.get("created_at");
                        java.time.OffsetDateTime createdAt = created == null
                                ? java.time.OffsetDateTime.now()
                                : java.time.OffsetDateTime.parse(String.valueOf(created));
                        if (!visitor.test(new StoredFile(key, createdAt))) {
                            return;
                        }
                    }
                }
                Object next = response.get("next_cursor");
                cursor = next == null ? null : String.valueOf(next);
            } while (cursor != null);
        } catch (MediaStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new MediaStorageException("Không liệt kê được ảnh trên Cloudinary", e);
        }
    }

    @Override
    public java.util.List<String> listFolders(String path) {
        java.util.List<String> names = new java.util.ArrayList<>();
        String cursor = null;
        try {
            do {
                Map<String, Object> options = new HashMap<>(ObjectUtils.asMap("max_results", 500));
                if (cursor != null) {
                    options.put("next_cursor", cursor);
                }
                Map<?, ?> response = cloudinary.api().subFolders(path, options);
                if (response.get("folders") instanceof java.util.List<?> folders) {
                    for (Object folder : folders) {
                        Object name = ((Map<?, ?>) folder).get("name");
                        if (name != null) {
                            names.add(String.valueOf(name));
                        }
                    }
                }
                Object next = response.get("next_cursor");
                cursor = next == null ? null : String.valueOf(next);
            } while (cursor != null);
        } catch (Exception e) {
            throw new MediaStorageException("Không liệt kê được thư mục trên Cloudinary", e);
        }
        return names;
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
