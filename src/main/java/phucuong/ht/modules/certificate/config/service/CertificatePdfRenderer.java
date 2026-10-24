package phucuong.ht.modules.certificate.config.service;

import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Vẽ PDF chứng nhận: mỗi trang = một ảnh nền + các phần tử text (và mã QR nếu bố cục có) đặt theo tọa độ tỉ lệ.
@Component
public class CertificatePdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(CertificatePdfRenderer.class);

    // Trang mặc định khi không có ảnh nền (A4 dọc, điểm = points).
    private static final float DEFAULT_WIDTH = PDRectangle.A4.getWidth();
    private static final float DEFAULT_HEIGHT = PDRectangle.A4.getHeight();

    public record RenderItem(String text, double x, double y, double fontSize, String color, String align,
                             String fontFamily, boolean bold, boolean italic, boolean underline) {
    }

    // Mã QR đã sinh xong: x,y là tâm (tỉ lệ 0..1), size là cạnh theo tỉ lệ chiều cao trang.
    public record RenderQr(BitMatrix matrix, double x, double y, double size) {
    }

    // pageWidth/pageHeight tính bằng điểm (points); <= 0 nghĩa là dùng kích thước ảnh nền (hoặc A4).
    public record RenderPage(byte[] background, List<RenderItem> items, List<RenderQr> qrCodes, float pageWidth, float pageHeight) {
    }

    public byte[] renderPdf(List<RenderPage> pages) {
        try (PDDocument document = new PDDocument()) {
            // Cache font theo "family|bold|italic" trong phạm vi một tài liệu (font PDFBox gắn với document).
            Map<String, PDType0Font> fontCache = new HashMap<>();

            for (RenderPage page : pages) {
                renderPage(document, page, fontCache);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo PDF chứng nhận: " + e.getMessage(), e);
        }
    }

    private PDType0Font font(PDDocument document, Map<String, PDType0Font> cache, String family, boolean bold, boolean italic) {
        String resource = fontResource(family, bold, italic);
        return cache.computeIfAbsent(resource, path -> {
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                return PDType0Font.load(document, is, true);
            } catch (Exception e) {
                throw new RuntimeException("Không thể nạp font: " + path, e);
            }
        });
    }

    // Ánh xạ họ chữ + đậm/nghiêng -> file TTF (có dấu tiếng Việt). Font một kiểu bỏ qua đậm/nghiêng.
    private String fontResource(String family, boolean bold, boolean italic) {
        String fam = family == null ? "sans" : family.toLowerCase();
        return switch (fam) {
            case "serif" -> "fonts/DejaVuSerif" + (bold && italic ? "-BoldItalic" : bold ? "-Bold" : italic ? "-Italic" : "") + ".ttf";
            case "bevietnam" -> "fonts/BeVietnamPro" + (bold && italic ? "-BoldItalic" : bold ? "-Bold" : italic ? "-Italic" : "-Regular") + ".ttf";
            case "lobster" -> "fonts/Lobster-Regular.ttf";
            case "pacifico" -> "fonts/Pacifico-Regular.ttf";
            case "patrickhand" -> "fonts/PatrickHand-Regular.ttf";
            default -> "fonts/DejaVuSans" + (bold && italic ? "-BoldOblique" : bold ? "-Bold" : italic ? "-Oblique" : "") + ".ttf";
        };
    }

    private void renderPage(PDDocument document, RenderPage page, Map<String, PDType0Font> fontCache) throws Exception {
        PDImageXObject image = null;
        if (page.background() != null && page.background().length > 0) {
            try {
                image = PDImageXObject.createFromByteArray(document, page.background(), "bg");
            } catch (Exception e) {
                // Ảnh nền không đọc được (định dạng không hỗ trợ, hỏng...) thì vẫn xuất PDF, chỉ thiếu nền.
                log.warn("Không đọc được ảnh nền chứng nhận, bỏ qua nền: {}", e.toString());
            }
        }

        // Ưu tiên khổ giấy đã chọn; nếu không có thì theo ảnh nền; cuối cùng là A4.
        float width;
        float height;
        if (page.pageWidth() > 0 && page.pageHeight() > 0) {
            width = page.pageWidth();
            height = page.pageHeight();
        } else if (image != null) {
            width = image.getWidth();
            height = image.getHeight();
        } else {
            width = DEFAULT_WIDTH;
            height = DEFAULT_HEIGHT;
        }

        PDPage pdPage = new PDPage(new PDRectangle(width, height));
        document.addPage(pdPage);

        try (PDPageContentStream cs = new PDPageContentStream(document, pdPage)) {
            if (image != null) {
                cs.drawImage(image, 0, 0, width, height);
            }
            if (page.qrCodes() != null) {
                for (RenderQr qr : page.qrCodes()) {
                    // Không nuốt lỗi QR: thiếu mã QR thì không được xuất PDF.
                    drawQr(cs, qr, width, height);
                }
            }
            if (page.items() != null) {
                for (RenderItem item : page.items()) {
                    try {
                        drawItem(document, cs, item, width, height, fontCache);
                    } catch (Exception e) {
                        // Một phần tử lỗi (font thiếu, ký tự lạ...) không làm hỏng cả chứng nhận.
                        log.warn("Bỏ qua phần tử chứng nhận do lỗi vẽ ({}): {}", item, e.toString());
                    }
                }
            }
        }
    }

    private void drawItem(PDDocument document, PDPageContentStream cs, RenderItem item, float pageWidth, float pageHeight,
                          Map<String, PDType0Font> fontCache) throws Exception {
        drawItem(document, cs, item, 0, 0, pageWidth, pageHeight, false, fontCache);
    }

    // Vẽ chữ trong khung (originX, originY = góc dưới-trái, boxWidth x boxHeight điểm); toạ độ và cỡ chữ của phần tử là tỉ lệ theo khung.
    // shrinkToFit: thu nhỏ chữ cho vừa bề ngang khung (dùng cho thẻ, tên dài không tràn sang thẻ bên cạnh).
    public void drawItem(PDDocument document, PDPageContentStream cs, RenderItem item, float originX, float originY,
                  float pageWidth, float pageHeight, boolean shrinkToFit, Map<String, PDType0Font> fontCache) throws Exception {
        String text = item.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        PDType0Font font = font(document, fontCache, item.fontFamily(), item.bold(), item.italic());
        text = sanitize(text, font);
        if (text.isEmpty()) {
            return;
        }

        float fontSizeRatio = item.fontSize() > 0 ? (float) item.fontSize() : 0.03f;
        float fontSize = fontSizeRatio * pageHeight;

        float anchorX = originX + (float) item.x() * pageWidth;
        // Toạ độ y tỉ lệ tính từ trên; PDF gốc dưới-trái nên quy đổi và trừ baseline.
        float topY = (float) item.y() * pageHeight;
        float baselineY = originY + pageHeight - topY - fontSize;

        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        String align = item.align() == null ? "left" : item.align();
        if (shrinkToFit && textWidth > 0) {
            float room = switch (align) {
                case "center" -> 2f * Math.min(anchorX - originX, originX + pageWidth - anchorX);
                case "right" -> anchorX - originX;
                default -> originX + pageWidth - anchorX;
            };
            if (room > 0 && textWidth > room) {
                fontSize *= room / textWidth;
                textWidth = room;
                baselineY = originY + pageHeight - topY - fontSize;
            }
        }
        float x = switch (align) {
            case "center" -> anchorX - textWidth / 2f;
            case "right" -> anchorX - textWidth;
            default -> anchorX;
        };

        Color color = parseColor(item.color());
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, baselineY);
        cs.showText(text);
        cs.endText();

        if (item.underline()) {
            float thickness = Math.max(0.5f, fontSize * 0.05f);
            float underlineY = baselineY - fontSize * 0.12f;
            cs.setNonStrokingColor(color);
            cs.addRect(x, underlineY, textWidth, thickness);
            cs.fill();
        }
    }

    // Vẽ mã QR dạng vector (sắc nét khi in): nền trắng kèm vùng yên tĩnh rồi các module đen gộp theo từng hàng.
    private void drawQr(PDPageContentStream cs, RenderQr qr, float pageWidth, float pageHeight) throws Exception {
        drawQr(cs, qr, 0, 0, pageWidth, pageHeight);
    }

    // Vẽ mã QR trong khung (originX, originY = góc dưới-trái); cạnh mã tính theo chiều cao khung.
    public void drawQr(PDPageContentStream cs, RenderQr qr, float originX, float originY, float pageWidth, float pageHeight) throws Exception {
        BitMatrix matrix = qr.matrix();
        if (matrix == null) {
            throw new IllegalStateException("Thiếu mã QR của chứng nhận");
        }
        double sizeRatio = qr.size() > 0 ? qr.size() : 0.15;
        float side = (float) sizeRatio * pageHeight;
        int modules = matrix.getWidth() + CertificateQrService.QUIET_ZONE * 2;
        float module = side / modules;
        float left = originX + (float) qr.x() * pageWidth - side / 2f;
        float top = originY + pageHeight - ((float) qr.y() * pageHeight - side / 2f);

        cs.setNonStrokingColor(Color.WHITE);
        cs.addRect(left, top - side, side, side);
        cs.fill();

        cs.setNonStrokingColor(Color.BLACK);
        for (int y = 0; y < matrix.getHeight(); y++) {
            int x = 0;
            while (x < matrix.getWidth()) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < matrix.getWidth() && matrix.get(x, y)) {
                    x++;
                }
                float rx = left + (start + CertificateQrService.QUIET_ZONE) * module;
                float ry = top - (y + CertificateQrService.QUIET_ZONE + 1) * module;
                cs.addRect(rx, ry, (x - start) * module, module);
            }
        }
        cs.fill();
    }

    // Loại bỏ ký tự font không mã hoá được để showText không ném lỗi.
    private String sanitize(String text, PDType0Font font) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String s = new String(Character.toChars(cp));
            try {
                font.getStringWidth(s);
                sb.append(s);
            } catch (Exception ignored) {
                // Ký tự không hỗ trợ: thay bằng khoảng trắng để giữ bố cục.
                sb.append(' ');
            }
        });
        return sb.toString().trim();
    }

    private Color parseColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return Color.BLACK;
        }
        try {
            return Color.decode(hex.trim());
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }
}
