package phucuong.ht.modules.certificate.config.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Vẽ PDF chứng nhận: mỗi trang = một ảnh nền + các phần tử text đặt theo tọa độ tỉ lệ.
@Component
public class CertificatePdfRenderer {

    // Trang mặc định khi không có ảnh nền (A4 dọc, điểm = points).
    private static final float DEFAULT_WIDTH = PDRectangle.A4.getWidth();
    private static final float DEFAULT_HEIGHT = PDRectangle.A4.getHeight();

    public record RenderItem(String text, double x, double y, double fontSize, String color, String align,
                             String fontFamily, boolean bold, boolean italic, boolean underline) {
    }

    public record RenderPage(byte[] background, List<RenderItem> items) {
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
        boolean serif = "serif".equalsIgnoreCase(family);
        String base = serif ? "DejaVuSerif" : "DejaVuSans";
        String suffix;
        if (serif) {
            suffix = bold && italic ? "-BoldItalic" : bold ? "-Bold" : italic ? "-Italic" : "";
        } else {
            suffix = bold && italic ? "-BoldOblique" : bold ? "-Bold" : italic ? "-Oblique" : "";
        }
        String resource = "fonts/" + base + suffix + ".ttf";
        return cache.computeIfAbsent(resource, path -> {
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                return PDType0Font.load(document, is, true);
            } catch (Exception e) {
                throw new RuntimeException("Không thể nạp font: " + path, e);
            }
        });
    }

    private void renderPage(PDDocument document, RenderPage page, Map<String, PDType0Font> fontCache) throws Exception {
        PDImageXObject image = null;
        float width = DEFAULT_WIDTH;
        float height = DEFAULT_HEIGHT;

        if (page.background() != null && page.background().length > 0) {
            image = PDImageXObject.createFromByteArray(document, page.background(), "bg");
            width = image.getWidth();
            height = image.getHeight();
        }

        PDPage pdPage = new PDPage(new PDRectangle(width, height));
        document.addPage(pdPage);

        try (PDPageContentStream cs = new PDPageContentStream(document, pdPage)) {
            if (image != null) {
                cs.drawImage(image, 0, 0, width, height);
            }
            if (page.items() != null) {
                for (RenderItem item : page.items()) {
                    drawItem(document, cs, item, width, height, fontCache);
                }
            }
        }
    }

    private void drawItem(PDDocument document, PDPageContentStream cs, RenderItem item, float pageWidth, float pageHeight,
                          Map<String, PDType0Font> fontCache) throws Exception {
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

        float anchorX = (float) item.x() * pageWidth;
        // Toạ độ y tỉ lệ tính từ trên; PDF gốc dưới-trái nên quy đổi và trừ baseline.
        float topY = (float) item.y() * pageHeight;
        float baselineY = pageHeight - topY - fontSize;

        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        String align = item.align() == null ? "left" : item.align();
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
