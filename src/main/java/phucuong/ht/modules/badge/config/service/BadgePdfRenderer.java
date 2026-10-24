package phucuong.ht.modules.badge.config.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.certificate.config.service.CertificatePdfRenderer;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Vẽ PDF thẻ tham dự: mỗi trang là một tờ giấy chứa nhiều thẻ; mỗi thẻ = ảnh nền + chữ + mã QR theo tọa độ tỉ lệ so với thẻ.
// Dùng chung cách vẽ chữ / QR với PDF chứng nhận nhưng thu nhỏ chữ vừa bề ngang thẻ và cắt phần tràn ra ngoài thẻ.
@Component
@RequiredArgsConstructor
public class BadgePdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(BadgePdfRenderer.class);

    private final CertificatePdfRenderer base;

    // Nội dung một mặt của một thẻ. background là mảng byte dùng chung giữa các thẻ (so sánh theo tham chiếu) để PDF chỉ nhúng ảnh một lần.
    public record Side(byte[] background, List<CertificatePdfRenderer.RenderItem> items, List<CertificatePdfRenderer.RenderQr> qrCodes) {
    }

    // xMm, yMm: góc trên-trái của thẻ trên trang (mm).
    public record Placed(Side side, double xMm, double yMm) {
    }

    public record Sheet(double widthMm, double heightMm, double cardWidthMm, double cardHeightMm, boolean cropMarks, List<Placed> cards) {
    }

    public byte[] render(List<Sheet> sheets) {
        try (PDDocument document = new PDDocument()) {
            Map<String, PDType0Font> fontCache = new HashMap<>();
            Map<byte[], PDImageXObject> imageCache = new IdentityHashMap<>();
            for (Sheet sheet : sheets) {
                renderSheet(document, sheet, fontCache, imageCache);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo PDF thẻ tham dự: " + e.getMessage(), e);
        }
    }

    private void renderSheet(PDDocument document, Sheet sheet, Map<String, PDType0Font> fontCache,
                             Map<byte[], PDImageXObject> imageCache) throws Exception {
        float pageWidth = (float) BadgeSheetLayout.toPoints(sheet.widthMm());
        float pageHeight = (float) BadgeSheetLayout.toPoints(sheet.heightMm());
        float cardWidth = (float) BadgeSheetLayout.toPoints(sheet.cardWidthMm());
        float cardHeight = (float) BadgeSheetLayout.toPoints(sheet.cardHeightMm());

        PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
        document.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            for (Placed placed : sheet.cards()) {
                float originX = (float) BadgeSheetLayout.toPoints(placed.xMm());
                float originY = pageHeight - (float) BadgeSheetLayout.toPoints(placed.yMm()) - cardHeight;
                drawCard(document, cs, placed.side(), originX, originY, cardWidth, cardHeight, fontCache, imageCache);
                if (sheet.cropMarks()) {
                    cs.setStrokingColor(new Color(150, 150, 150));
                    cs.setLineWidth(0.25f);
                    cs.addRect(originX, originY, cardWidth, cardHeight);
                    cs.stroke();
                }
            }
        }
    }

    private void drawCard(PDDocument document, PDPageContentStream cs, Side side, float originX, float originY,
                          float width, float height, Map<String, PDType0Font> fontCache,
                          Map<byte[], PDImageXObject> imageCache) throws Exception {
        cs.saveGraphicsState();
        // Phần tràn ra ngoài thẻ (chữ, mã QR đặt sát mép) bị cắt để không đè lên thẻ bên cạnh.
        cs.addRect(originX, originY, width, height);
        cs.clip();

        PDImageXObject image = image(document, side.background(), imageCache);
        if (image != null) {
            cs.drawImage(image, originX, originY, width, height);
        }
        if (side.qrCodes() != null) {
            for (CertificatePdfRenderer.RenderQr qr : side.qrCodes()) {
                // Không nuốt lỗi QR: thiếu mã QR thì không được xuất PDF.
                base.drawQr(cs, qr, originX, originY, width, height);
            }
        }
        if (side.items() != null) {
            for (CertificatePdfRenderer.RenderItem item : side.items()) {
                try {
                    base.drawItem(document, cs, item, originX, originY, width, height, true, fontCache);
                } catch (Exception e) {
                    // Một phần tử lỗi (font thiếu, ký tự lạ...) không làm hỏng cả thẻ.
                    log.warn("Bỏ qua phần tử thẻ do lỗi vẽ ({}): {}", item, e.toString());
                }
            }
        }
        cs.restoreGraphicsState();
    }

    private PDImageXObject image(PDDocument document, byte[] background, Map<byte[], PDImageXObject> cache) {
        if (background == null || background.length == 0) {
            return null;
        }
        PDImageXObject cached = cache.get(background);
        if (cached != null) {
            return cached;
        }
        try {
            PDImageXObject created = PDImageXObject.createFromByteArray(document, background, "badge-bg");
            cache.put(background, created);
            return created;
        } catch (Exception e) {
            // Ảnh nền không đọc được (định dạng không hỗ trợ, hỏng...) thì vẫn xuất PDF, chỉ thiếu nền.
            log.warn("Không đọc được ảnh nền thẻ, bỏ qua nền: {}", e.toString());
            cache.put(background, null);
            return null;
        }
    }
}
