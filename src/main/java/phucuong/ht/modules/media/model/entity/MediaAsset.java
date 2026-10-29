package phucuong.ht.modules.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.model.enums.MediaStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Siêu dữ liệu một ảnh đã lưu trên cloud. Bản ghi nghiệp vụ chỉ giữ id của asset này,
 * không giữ URL của nhà cung cấp nên đổi nhà cung cấp không ảnh hưởng dữ liệu.
 */
@Entity
@Table(name = "media_asset")
@Getter
@Setter
@NoArgsConstructor
public class MediaAsset {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private MediaCategory category;

    /** Null = thư mục system (cấu hình dùng chung toàn hệ thống) hoặc chưa gắn. */
    @Column(name = "diocese_id")
    private UUID dioceseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MediaStatus status;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "base_key", nullable = false, length = 400)
    private String baseKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variants", nullable = false, columnDefinition = "jsonb")
    private List<MediaVariantInfo> variants = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "migration_error", length = 500)
    private String migrationError;

    @Column(name = "migration_retries", nullable = false)
    private int migrationRetries;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    public Optional<MediaVariantInfo> variant(String name) {
        return variants.stream().filter(v -> v.name().equals(name)).findFirst();
    }
}
