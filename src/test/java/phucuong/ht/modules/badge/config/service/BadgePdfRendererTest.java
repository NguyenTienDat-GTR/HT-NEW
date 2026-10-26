package phucuong.ht.modules.badge.config.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.certificate.config.service.CertificatePdfRenderer;
import phucuong.ht.modules.certificate.config.service.CertificatePdfRenderer.RenderItem;
import phucuong.ht.modules.certificate.config.service.CertificatePdfRenderer.RenderQr;
import phucuong.ht.modules.training.courseAttendance.service.AttendanceTokenService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgePdfRendererTest {

    private final BadgePdfRenderer renderer = new BadgePdfRenderer(new CertificatePdfRenderer());
    private final AttendanceTokenService tokens = new AttendanceTokenService("unit-test-attendance-secret-0123456789");

    private static RenderItem text(String value, double x, double y, double fontSize, String align) {
        return new RenderItem(value, x, y, fontSize, "#000000", align, "sans", false, false, false);
    }

    private BadgePdfRenderer.Side side(byte[] background, String... lines) {
        List<RenderItem> items = new ArrayList<>();
        double y = 0.1;
        for (String line : lines) {
            items.add(text(line, 0.5, y, 0.1, "center"));
            y += 0.2;
        }
        return new BadgePdfRenderer.Side(background, items, List.of());
    }

    private BadgePdfRenderer.Sheet sheet(boolean cropMarks, BadgePdfRenderer.Side... sides) {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0);
        List<BadgePdfRenderer.Placed> placed = new ArrayList<>();
        for (int i = 0; i < sides.length; i++) {
            double[] position = grid.position(i, false, false);
            placed.add(new BadgePdfRenderer.Placed(sides[i], position[0], position[1]));
        }
        return new BadgePdfRenderer.Sheet(210, 297, 90, 55, cropMarks, placed);
    }

    private static byte[] noisePng(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String textOf(byte[] pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document).replaceAll("\\s+", " ").trim();
        }
    }

    @Test
    void rendersOnePagePerSheetWithTheSheetDimensions() throws Exception {
        byte[] pdf = renderer.render(List.of(sheet(true, side(null, "An")), sheet(true, side(null, "Bình"))));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(2, document.getNumberOfPages());
            assertEquals(BadgeSheetLayout.toPoints(210), document.getPage(0).getMediaBox().getWidth(), 0.01);
            assertEquals(BadgeSheetLayout.toPoints(297), document.getPage(0).getMediaBox().getHeight(), 0.01);
        }
    }

    @Test
    void landscapeSheetKeepsItsOrientation() throws Exception {
        BadgePdfRenderer.Sheet landscape = new BadgePdfRenderer.Sheet(297, 210, 90, 55, false,
                List.of(new BadgePdfRenderer.Placed(side(null, "An"), 15, 15)));
        try (PDDocument document = Loader.loadPDF(renderer.render(List.of(landscape)))) {
            assertTrue(document.getPage(0).getMediaBox().getWidth() > document.getPage(0).getMediaBox().getHeight());
        }
    }

    @Test
    void drawsVietnameseTextOfEveryCard() throws Exception {
        byte[] pdf = renderer.render(List.of(sheet(false,
                side(null, "Giuse Nguyễn Văn Ân", "Giáo xứ Phú Cường"),
                side(null, "Maria Trần Thị Ngọc", "Đội 2"))));
        String text = textOf(pdf, 1);
        assertTrue(text.contains("Giuse Nguyễn Văn Ân"), text);
        assertTrue(text.contains("Giáo xứ Phú Cường"), text);
        assertTrue(text.contains("Maria Trần Thị Ngọc"), text);
        assertTrue(text.contains("Đội 2"), text);
    }

    @Test
    void emptySheetListProducesAnEmptyDocument() throws Exception {
        try (PDDocument document = Loader.loadPDF(renderer.render(List.of()))) {
            assertEquals(0, document.getNumberOfPages());
        }
    }

    @Test
    void sheetWithoutCardsStillProducesABlankPage() throws Exception {
        try (PDDocument document = Loader.loadPDF(renderer.render(List.of(sheet(true))))) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void sharedBackgroundIsEmbeddedOnlyOnce() throws Exception {
        byte[] background = noisePng(220);
        BadgePdfRenderer.Side shared = side(background, "An");
        byte[] one = renderer.render(List.of(sheet(false, shared)));
        BadgePdfRenderer.Side[] ten = new BadgePdfRenderer.Side[10];
        java.util.Arrays.fill(ten, shared);
        byte[] many = renderer.render(List.of(sheet(false, ten)));
        assertTrue(many.length < one.length * 1.3, "10 thẻ dùng chung ảnh nền phải nhẹ gần bằng 1 thẻ: " + one.length + " vs " + many.length);
        assertTrue(one.length > 100_000, "ảnh nền phải được nhúng");
    }

    @Test
    void unreadableBackgroundIsSkippedInsteadOfFailingTheExport() throws Exception {
        byte[] pdf = renderer.render(List.of(sheet(false, side(new byte[]{1, 2, 3, 4}, "An"))));
        assertTrue(textOf(pdf, 1).contains("An"));
    }

    @Test
    void longNamesAreShrunkToFitTheCardWidth() throws Exception {
        String longName = "Giuse Nguyễn Văn Trường Thịnh Phát Đạt Hưng Vượng Khang Ninh Thái Bình An";
        byte[] pdf = renderer.render(List.of(sheet(false, side(null, longName))));
        // Không bị cắt ngắn, vẫn đọc ra đủ tên (chữ được thu nhỏ chứ không bị bỏ).
        assertTrue(textOf(pdf, 1).contains("Khang Ninh Thái Bình An"));
    }

    @Test
    void textOutsideTheCardIsClippedAwayFromNeighbours() throws Exception {
        // Chữ đặt tại x = 1.4 (ngoài thẻ) vẫn tạo PDF hợp lệ; phần ngoài thẻ bị cắt bởi vùng cắt của thẻ.
        BadgePdfRenderer.Side outside = new BadgePdfRenderer.Side(null, List.of(text("Ngoài", 1.4, 0.5, 0.1, "left")), List.of());
        try (PDDocument document = Loader.loadPDF(renderer.render(List.of(sheet(false, outside))))) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void rendersAttendanceQrWithoutBreakingTheText() throws Exception {
        RenderQr qr = new RenderQr(tokens.matrixFor("KH-1", "participation-1"), 0.8, 0.5, 0.5);
        BadgePdfRenderer.Side withQr = new BadgePdfRenderer.Side(null, List.of(text("Có QR", 0.3, 0.4, 0.1, "center")), List.of(qr));
        byte[] pdf = renderer.render(List.of(sheet(true, withQr)));
        assertTrue(textOf(pdf, 1).contains("Có QR"));
        assertTrue(pdf.length > 1500);
    }

    @Test
    void cropMarksAddDrawingOperations() throws Exception {
        BadgePdfRenderer.Side plain = side(null, "An");
        byte[] without = renderer.render(List.of(sheet(false, plain, plain, plain)));
        byte[] with = renderer.render(List.of(sheet(true, plain, plain, plain)));
        assertTrue(with.length != without.length);
    }

    @Test
    void oneBadTextItemDoesNotBreakTheCard() throws Exception {
        List<RenderItem> items = List.of(
                new RenderItem("Tốt", 0.5, 0.2, 0.1, "not-a-color", "center", "unknown-font", true, true, true),
                text("Vẫn có", 0.5, 0.5, 0.1, "center"));
        byte[] pdf = renderer.render(List.of(sheet(false, new BadgePdfRenderer.Side(null, items, List.of()))));
        String text = textOf(pdf, 1);
        assertTrue(text.contains("Tốt"));
        assertTrue(text.contains("Vẫn có"));
    }

    @Test
    void nullItemListsAreTolerated() throws Exception {
        try (PDDocument document = Loader.loadPDF(renderer.render(List.of(sheet(false, new BadgePdfRenderer.Side(null, null, null)))))) {
            assertEquals(1, document.getNumberOfPages());
        }
    }
}
