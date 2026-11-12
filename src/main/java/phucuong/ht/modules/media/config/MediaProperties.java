package phucuong.ht.modules.media.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình lưu trữ ảnh (app.media.*). Nhà cung cấp lưu trữ được chọn bằng {@code active-provider}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    /**
     * Tên nhà cung cấp đang dùng để ghi ảnh mới: cloudinary | local | ...
     */
    private String activeProvider;

    /**
     * Tiền tố gốc của mọi khóa lưu trữ, ví dụ c-lead/prod (tách môi trường trên cùng một tài khoản cloud).
     */
    private String rootPrefix = "c-lead/dev";

    /**
     * Khóa ký HMAC cho URL ảnh. Để trống thì suy ra từ khóa JWT (chỉ nên dùng khi phát triển).
     */
    private String signingSecret;

    /**
     * Đường dẫn gốc của endpoint phát ảnh.
     */
    private String urlPath = "/api/media";

    private long maxAvatarBytes = 5L * 1024 * 1024;
    private long maxBackgroundBytes = 15L * 1024 * 1024;
    /**
     * Chặn ảnh có số điểm ảnh quá lớn (pixel bomb).
     */
    private long maxPixels = 40_000_000L;

    /**
     * Bộ nhớ đệm trong tiến trình cho biến thể nhỏ (thumb/medium/preview).
     */
    private long cacheMaxBytes = 64L * 1024 * 1024;
    private long cacheMaxEntryBytes = 512L * 1024;

    /** Số ảnh nền tối đa trong album của mỗi đơn vị cho mỗi loại (chứng nhận / thẻ). */
    private int albumMaxItems = 3;

    private Local local = new Local();
    private Cloudinary cloudinary = new Cloudinary();
    private LegacyMigration legacyMigration = new LegacyMigration();
    private ProviderMigration providerMigration = new ProviderMigration();
    private Cleanup cleanup = new Cleanup();

    @Getter
    @Setter
    public static class Local {
        private boolean enabled = false;
        private String dir = "./uploads/media";
    }

    @Getter
    @Setter
    public static class Cloudinary {
        /**
         * cloudinary://API_KEY:API_SECRET@CLOUD_NAME. Trống thì không tạo provider Cloudinary.
         */
        private String url;
        /**
         * Bật một lần để đưa các ảnh đã tải lên trước đây (đang nằm ở thư mục gốc Home) vào đúng thư mục giáo phận / loại ảnh.
         * Chạy khi ứng dụng khởi động xong; chạy lại nhiều lần vẫn an toàn.
         */
        private boolean syncFoldersOnStartup = false;
    }

    @Getter
    @Setter
    public static class LegacyMigration {
        private boolean enabled = false;
        private long fixedDelayMs = 30_000;
        private long initialDelayMs = 20_000;
        private int batchSize = 20;
    }

    @Getter
    @Setter
    public static class ProviderMigration {
        private boolean enabled = false;
        private long fixedDelayMs = 30_000;
        private long initialDelayMs = 30_000;
        private int batchSize = 20;
        /**
         * Xóa bản trên nhà cung cấp cũ sau khi đã chép và đối chiếu sha256 thành công.
         */
        private boolean deleteSource = false;
        private int maxRetries = 8;
    }

    @Getter
    @Setter
    public static class Cleanup {
        private boolean enabled = true;
        /** Chu kỳ chạy job; đủ ngắn để ảnh pending sống không quá nhiều so với pendingTtl. */
        private long fixedDelayMs = 300_000;
        private long initialDelayMs = 120_000;
        /** Ảnh tải lên mà chưa gắn vào bản ghi nghiệp vụ được giữ tối đa chừng này rồi bị xóa hẳn (không qua deletedRetention). */
        private Duration pendingTtl = Duration.ofHours(1);
        private Duration deletedRetention = Duration.ofDays(7);
        private Duration orphanGrace = Duration.ofDays(1);
        private int batchSize = 50;
    }
}
