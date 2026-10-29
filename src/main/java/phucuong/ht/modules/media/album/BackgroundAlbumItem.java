package phucuong.ht.modules.media.album;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import phucuong.ht.modules.media.model.enums.MediaCategory;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Một ảnh trong album ảnh nền của một đơn vị (giáo phận / giáo hạt / hệ thống). */
@Entity
@Table(name = "background_album_item")
@Getter
@Setter
@NoArgsConstructor
public class BackgroundAlbumItem {

    @Id
    @Column(name = "id")
    private UUID id;

    /** SYSTEM | DIOCESE:{id} | DEANERY:{id}: chỉ đơn vị này mới thấy và dùng ảnh. */
    @Column(name = "scope_key", nullable = false, length = 80)
    private String scopeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private MediaCategory category;

    /** CERTIFICATE | BADGE_FRONT | BADGE_BACK: mỗi loại là một album riêng. */
    @Column(name = "album_kind", nullable = false, length = 20)
    private String albumKind;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
