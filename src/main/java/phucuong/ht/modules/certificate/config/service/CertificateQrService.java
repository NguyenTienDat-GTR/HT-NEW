package phucuong.ht.modules.certificate.config.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// Sinh mã QR chứa mã chứng nhận. Chỉ dùng khi bố cục cấu hình có phần tử QR; kết quả chỉ sống trong bộ nhớ
// trong lúc xuất PDF / xem trước, hệ thống không lưu mã QR.
@Service
public class CertificateQrService {

    // Số module trắng bao quanh (vùng yên tĩnh) để máy quét nhận được mã kể cả khi đặt trên nền tối.
    public static final int QUIET_ZONE = 2;

    private final Executor qrExecutor;

    public CertificateQrService(@Qualifier("certificateQrExecutor") Executor qrExecutor) {
        this.qrExecutor = qrExecutor;
    }

    // Sinh mã QR chạy nền song song; người gọi phải chờ (join) trước khi dựng PDF.
    public CompletableFuture<BitMatrix> generateAsync(String certificateCode) {
        return CompletableFuture.supplyAsync(() -> generate(certificateCode), qrExecutor);
    }

    public BitMatrix generate(String certificateCode) {
        if (certificateCode == null || certificateCode.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã chứng nhận để tạo mã QR");
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        // Không thêm lề ở đây; vùng yên tĩnh được vẽ riêng (QUIET_ZONE) để kích thước module đồng đều.
        hints.put(EncodeHintType.MARGIN, 0);
        try {
            // Kích thước 1x1 => ZXing trả ma trận đúng số module (không phóng to).
            return new QRCodeWriter().encode(certificateCode.trim(), BarcodeFormat.QR_CODE, 1, 1, hints);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo mã QR cho chứng nhận " + certificateCode + ": " + e.getMessage(), e);
        }
    }

    // Ảnh SVG (data URL) của mã QR cho bản xem trước trên giao diện; không lưu lại.
    public String toSvgDataUrl(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int total = width + QUIET_ZONE * 2;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < width && matrix.get(x, y)) {
                    x++;
                }
                path.append('M').append(start + QUIET_ZONE).append(' ').append(y + QUIET_ZONE)
                        .append('h').append(x - start).append("v1h-").append(x - start).append('z');
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + total + " " + total
                + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/><path fill=\"#000000\" d=\""
                + path + "\"/></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
