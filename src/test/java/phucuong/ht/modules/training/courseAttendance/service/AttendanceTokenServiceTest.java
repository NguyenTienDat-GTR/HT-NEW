package phucuong.ht.modules.training.courseAttendance.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceTokenServiceTest {

    private static final String SECRET = "unit-test-attendance-secret-0123456789";

    private final AttendanceTokenService service = new AttendanceTokenService(SECRET);

    private static final String COURSE = "KH-2026-1";

    @Test
    void tokenRoundTrips() {
        String id = UUID.randomUUID().toString();
        String token = service.tokenFor(COURSE, id);
        assertTrue(token.startsWith("HTATT2." + COURSE + "." + id + "."));
        AttendanceTokenService.Verified verified = service.verify(token).orElseThrow();
        assertEquals(COURSE, verified.courseCode());
        assertEquals(id, verified.participationId());
    }

    @Test
    void verifyToleratesSurroundingWhitespaceFromScanners() {
        String id = UUID.randomUUID().toString();
        assertEquals(id, service.verify("  " + service.tokenFor(COURSE, id) + "\n").orElseThrow().participationId());
    }

    @Test
    void tokenIsDeterministicForTheSameParticipation() {
        String id = UUID.randomUUID().toString();
        assertEquals(service.tokenFor(COURSE, id), service.tokenFor(COURSE, id));
        assertNotEquals(service.tokenFor(COURSE, id), service.tokenFor(COURSE, UUID.randomUUID().toString()));
        assertNotEquals(service.tokenFor(COURSE, id), service.tokenFor("KH-2026-2", id));
    }

    @Test
    void tamperedCourseIdOrSignatureIsRejected() {
        String id = UUID.randomUUID().toString();
        String token = service.tokenFor(COURSE, id);
        assertTrue(service.verify(token.replace(id, UUID.randomUUID().toString())).isEmpty());
        assertTrue(service.verify(token.replace(COURSE, "KH-2026-2")).isEmpty());

        String[] parts = token.split("\\.");
        String flipped = parts[3].substring(0, parts[3].length() - 1) + (parts[3].endsWith("A") ? "B" : "A");
        assertTrue(service.verify(parts[0] + "." + parts[1] + "." + parts[2] + "." + flipped).isEmpty());
        assertTrue(service.verify(parts[0] + "." + parts[1] + "." + parts[2] + ".").isEmpty());
        assertTrue(service.verify(parts[0] + "." + parts[1] + "." + parts[2]).isEmpty());
    }

    @Test
    void wrongPrefixOrShapeIsRejected() {
        String id = UUID.randomUUID().toString();
        String token = service.tokenFor(COURSE, id);
        assertTrue(service.verify(token.replace("HTATT2", "HTATT3")).isEmpty());
        assertTrue(service.verify(null).isEmpty());
        assertTrue(service.verify("").isEmpty());
        assertTrue(service.verify("   ").isEmpty());
        assertTrue(service.verify("https://example.com/certificates/ABC").isEmpty());
        assertTrue(service.verify(token + ".extra").isEmpty());
        assertTrue(service.verify("HTATT2.." + id + ".sig").isEmpty());
    }

    @Test
    void legacyTokenIsRecognisedButNeverVerified() {
        String legacy = "HTATT1.some-participation.AAAAAAAAAAAAAAAAAAAAAA";
        assertTrue(service.isLegacy(legacy));
        assertTrue(service.isLegacy("  " + legacy + "\n"));
        assertTrue(service.verify(legacy).isEmpty());
        assertFalse(service.isLegacy(service.tokenFor(COURSE, "p1")));
        assertFalse(service.isLegacy(null));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String id = UUID.randomUUID().toString();
        AttendanceTokenService other = new AttendanceTokenService("another-secret-0123456789-abcdef");
        assertTrue(service.verify(other.tokenFor(COURSE, id)).isEmpty());
    }

    @Test
    void refusesToSignInvalidCourseOrParticipationIds() {
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor(COURSE, null));
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor(COURSE, " "));
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor(COURSE, "a.b"));
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor(null, "p1"));
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor("KH.1", "p1"));
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThrows(IllegalStateException.class, () -> new AttendanceTokenService(null));
        assertThrows(IllegalStateException.class, () -> new AttendanceTokenService(""));
        assertThrows(IllegalStateException.class, () -> new AttendanceTokenService("short"));
    }

    @Test
    void matrixEncodesTheToken() {
        BitMatrix matrix = service.matrixFor(COURSE, UUID.randomUUID().toString());
        assertTrue(matrix.getWidth() >= 21);
        assertEquals(matrix.getWidth(), matrix.getHeight());
    }

    @Test
    void pngDecodesBackToTheSignedToken() throws Exception {
        String id = UUID.randomUUID().toString();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(service.pngFor(COURSE, id, 8)));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        // Ảnh do chính hệ thống vẽ (sạch, không xoay) nên đọc ở chế độ PURE_BARCODE. Chế độ mặc định dò mã như ảnh chụp
        // và thỉnh thoảng không tìm ra mã với một số ID ngẫu nhiên (khoảng 0,6%), làm test lúc đạt lúc không.
        String decoded = new QRCodeReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
                        Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE))
                .getText();
        assertEquals(service.tokenFor(COURSE, id), decoded);
        assertEquals(id, service.verify(decoded).orElseThrow().participationId());
    }

    @Test
    void pngHasQuietZoneAroundTheCode() throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(service.pngFor(COURSE, UUID.randomUUID().toString(), 4)));
        int quiet = AttendanceTokenService.QUIET_ZONE * 4;
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < quiet; j++) {
                assertEquals(0xFFFFFF, image.getRGB(i, j) & 0xFFFFFF);
                assertEquals(0xFFFFFF, image.getRGB(j, i) & 0xFFFFFF);
            }
        }
        assertFalse(image.getWidth() % 4 != 0);
    }
}
