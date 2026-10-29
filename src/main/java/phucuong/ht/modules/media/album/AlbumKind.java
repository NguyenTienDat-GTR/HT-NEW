package phucuong.ht.modules.media.album;

import lombok.Getter;
import phucuong.ht.modules.media.model.enums.MediaCategory;

/**
 * Loại album ảnh nền: chứng nhận, mặt trước của thẻ, mặt sau của thẻ. Mỗi loại là một album riêng
 * (mỗi đơn vị tối đa N ảnh cho mỗi loại).
 */
@Getter
public enum AlbumKind {

    CERTIFICATE(MediaCategory.CERTIFICATE_BACKGROUND, "training.certificate_config."),
    BADGE_FRONT(MediaCategory.BADGE_BACKGROUND, "training.badge_config."),
    BADGE_BACK(MediaCategory.BADGE_BACKGROUND, "training.badge_config.");

    private final MediaCategory category;
    /** Tiền tố quyền của thư viện cấu hình tương ứng. */
    private final String permissionPrefix;

    AlbumKind(MediaCategory category, String permissionPrefix) {
        this.category = category;
        this.permissionPrefix = permissionPrefix;
    }
}
