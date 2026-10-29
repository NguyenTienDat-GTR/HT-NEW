package phucuong.ht.modules.media.service;

import java.util.Base64;

/** Giải mã chuỗi data URL base64 cũ (dữ liệu trước khi chuyển lên cloud). */
public final class DataUrls {

    private DataUrls() {
    }

    public static boolean isDataUrl(String value) {
        return value != null && value.startsWith("data:") && value.indexOf(',') > 0;
    }

    /** @return null nếu rỗng hoặc không giải mã được. */
    public static byte[] decode(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        String base64 = dataUrl;
        int comma = dataUrl.indexOf(',');
        if (dataUrl.startsWith("data:") && comma >= 0) {
            base64 = dataUrl.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
