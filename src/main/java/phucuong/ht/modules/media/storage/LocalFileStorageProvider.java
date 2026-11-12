package phucuong.ht.modules.media.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.config.MediaProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Lưu ảnh trên thư mục cục bộ. Dùng khi phát triển / chạy thử và làm nhà cung cấp thứ hai
 * để kiểm thử việc chuyển dữ liệu giữa các nhà cung cấp.
 */
@Component
@ConditionalOnProperty(prefix = "app.media.local", name = "enabled", havingValue = "true")
public class LocalFileStorageProvider implements MediaStorageProvider {

    public static final String NAME = "local";

    private final Path root;

    public LocalFileStorageProvider(MediaProperties properties) {
        this.root = Path.of(properties.getLocal().getDir()).toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, data);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MediaStorageException("Không ghi được ảnh vào thư mục cục bộ", e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (NoSuchFileException e) {
            throw new MediaStorageException("Không tìm thấy ảnh: " + key, true);
        } catch (IOException e) {
            throw new MediaStorageException("Không đọc được ảnh từ thư mục cục bộ", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new MediaStorageException("Không xóa được ảnh trong thư mục cục bộ", e);
        }
    }

    @Override
    public void ensureFolder(String prefix) {
        try {
            Files.createDirectories(resolve(prefix));
        } catch (IOException e) {
            throw new MediaStorageException("Không tạo được thư mục ảnh", e);
        }
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new MediaStorageException("Khóa lưu trữ không hợp lệ", false);
        }
        return resolved;
    }
}
