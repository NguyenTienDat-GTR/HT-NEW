package phucuong.ht.modules.training.courseAttendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

// Mã QR điểm danh của hồ sơ trong khóa huấn luyện. Nội dung "HTATT1.<mã hồ sơ>.<chữ ký>": chữ ký HMAC-SHA256 (16 byte đầu)
// nên không đoán hay làm giả được; không phải đường dẫn, người ngoài quét không mở được trang nào.
// Mã chỉ sống trong bộ nhớ khi xuất thẻ / gửi email, hệ thống không lưu ảnh QR.
@Service
public class AttendanceTokenService {

    public static final String PREFIX = "HTATT1";
    public static final int QUIET_ZONE = 2;

    private static final int SIGNATURE_BYTES = 16;
    private static final int MIN_SECRET_LENGTH = 16;

    private final byte[] secret;

    public AttendanceTokenService(@Value("${app.attendance.qr-secret:${app.security.jwt.access-secret:}}") String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("Thiếu khóa ký mã QR điểm danh (app.attendance.qr-secret hoặc app.security.jwt.access-secret)");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String tokenFor(String participationId) {
        if (participationId == null || participationId.isBlank() || participationId.contains(".")) {
            throw new IllegalArgumentException("Mã hồ sơ không hợp lệ để tạo mã QR điểm danh");
        }
        return PREFIX + "." + participationId + "." + sign(participationId);
    }

    // Trả mã hồ sơ nếu chuỗi quét được hợp lệ (đúng định dạng và chữ ký); ngược lại rỗng.
    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 3 || !PREFIX.equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            return Optional.empty();
        }
        byte[] expected = sign(parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual) ? Optional.of(parts[1]) : Optional.empty();
    }

    private String sign(String participationId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(("att1|" + participationId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, SIGNATURE_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không thể ký mã QR điểm danh", e);
        }
    }

    // Ma trận QR của chuỗi token (không lề; vùng yên tĩnh được vẽ riêng khi dựng PDF / ảnh).
    public BitMatrix matrixFor(String participationId) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        try {
            return new QRCodeWriter().encode(tokenFor(participationId), BarcodeFormat.QR_CODE, 1, 1, hints);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo mã QR điểm danh cho hồ sơ " + participationId + ": " + e.getMessage(), e);
        }
    }

    // Ảnh PNG của mã QR (có vùng yên tĩnh) để nhúng vào email; moduleSize là số điểm ảnh mỗi module.
    public byte[] pngFor(String participationId, int moduleSize) {
        BitMatrix matrix = matrixFor(participationId);
        int size = (matrix.getWidth() + QUIET_ZONE * 2) * moduleSize;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int white = 0xFFFFFF;
        int black = 0x000000;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int mx = x / moduleSize - QUIET_ZONE;
                int my = y / moduleSize - QUIET_ZONE;
                boolean dark = mx >= 0 && my >= 0 && mx < matrix.getWidth() && my < matrix.getHeight() && matrix.get(mx, my);
                image.setRGB(x, y, dark ? black : white);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("Không có bộ mã hóa PNG");
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không thể tạo ảnh mã QR điểm danh: " + e.getMessage(), e);
        }
    }
}
