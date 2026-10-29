package phucuong.ht.modules.media.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.model.enums.MediaCategory;

import java.util.UUID;

/**
 * Tạo URL ảnh đã ký để đưa vào DTO trả về frontend. Chỉ gọi trong luồng đã qua kiểm tra quyền xem bản ghi chứa ảnh.
 * Trong thời gian chưa chuyển hết dữ liệu cũ, bản ghi chưa có asset sẽ trả lại chuỗi data URL cũ (nếu còn).
 */
@Component
@RequiredArgsConstructor
public class MediaUrls {

    private final MediaUrlSigner signer;

    /** URL biến thể hiển thị chi tiết / xem trước. */
    public String display(UUID assetId, MediaCategory category) {
        return assetId == null ? null : signer.url(assetId, category.getDisplayVariant());
    }

    /** URL biến thể nhỏ nhất, dùng cho danh sách. */
    public String small(UUID assetId, MediaCategory category) {
        return assetId == null ? null : signer.url(assetId, category.getSmallVariant());
    }

    public String displayOrLegacy(UUID assetId, String legacyDataUrl, MediaCategory category) {
        if (assetId != null) {
            return display(assetId, category);
        }
        return blankToNull(legacyDataUrl);
    }

    public String smallOrLegacy(UUID assetId, String legacyDataUrl, MediaCategory category) {
        if (assetId != null) {
            return small(assetId, category);
        }
        return blankToNull(legacyDataUrl);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
