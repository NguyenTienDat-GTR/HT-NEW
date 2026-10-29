package phucuong.ht.modules.media.model.enums;

import lombok.Getter;

import java.util.List;

/**
 * Loại ảnh. Mỗi loại có thư mục riêng dưới thư mục giáo phận và bộ biến thể (kích thước) riêng.
 */
@Getter
public enum MediaCategory {

    LEADER_AVATAR("avatars", true, List.of(MediaVariants.THUMB, MediaVariants.MEDIUM), MediaVariants.MEDIUM, MediaVariants.THUMB),
    ACCOUNT_AVATAR("account-avatars", true, List.of(MediaVariants.THUMB, MediaVariants.MEDIUM), MediaVariants.MEDIUM, MediaVariants.THUMB),
    CERTIFICATE_BACKGROUND("certificate-backgrounds", false, List.of(MediaVariants.MASTER, MediaVariants.PREVIEW), MediaVariants.PREVIEW, MediaVariants.PREVIEW),
    BADGE_BACKGROUND("badge-backgrounds", false, List.of(MediaVariants.MASTER, MediaVariants.PREVIEW), MediaVariants.PREVIEW, MediaVariants.PREVIEW);

    /** Tên thư mục trên cloud. */
    private final String folder;
    /** Ảnh đại diện (cắt khổ dọc 4x6) hay ảnh nền (giữ tỉ lệ). */
    private final boolean avatar;
    private final List<String> variants;
    /** Biến thể dùng khi hiển thị chi tiết / xem trước. */
    private final String displayVariant;
    /** Biến thể nhỏ nhất, dùng cho danh sách. */
    private final String smallVariant;

    MediaCategory(String folder, boolean avatar, List<String> variants, String displayVariant, String smallVariant) {
        this.folder = folder;
        this.avatar = avatar;
        this.variants = variants;
        this.displayVariant = displayVariant;
        this.smallVariant = smallVariant;
    }
}
