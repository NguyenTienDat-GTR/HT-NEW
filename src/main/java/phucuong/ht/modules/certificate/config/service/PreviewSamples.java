package phucuong.ht.modules.certificate.config.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/** Mã QR và ảnh đại diện mẫu để dựng PDF xem trước cấu hình (không dùng dữ liệu thật của huynh trưởng). */
public final class PreviewSamples {

    private PreviewSamples() {
    }

    public static BitMatrix qr(String content) {
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được mã QR mẫu", e);
        }
    }

    /** Hình người xám trên nền sáng, tỉ lệ 2:3, giống ô giữ chỗ trong trình thiết kế. */
    public static byte[] photoPng() {
        try {
            int width = 240;
            int height = 360;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xE2, 0xE8, 0xF0));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(0x94, 0xA3, 0xB8));
            g.fillOval(width / 2 - 52, 70, 104, 104);
            g.fillOval(width / 2 - 100, 200, 200, 260);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được ảnh mẫu", e);
        }
    }
}
