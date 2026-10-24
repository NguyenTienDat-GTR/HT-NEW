package phucuong.ht.modules.training.courseAttendance.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceTokenServiceTest {

    private static final String SECRET = "unit-test-attendance-secret-0123456789";

    private final AttendanceTokenService service = new AttendanceTokenService(SECRET);

    @Test
    void tokenRoundTrips() {
        String id = UUID.randomUUID().toString();
        String token = service.tokenFor(id);
        assertTrue(token.startsWith("HTATT1." + id + "."));
        assertEquals(id, service.verify(token).orElseThrow());
    }

    @Test
    void verifyToleratesSurroundingWhitespaceFromScanners() {
        String id = UUID.randomUUID().toString();
        assertEquals(id, service.verify("  " + service.tokenFor(id) + "\r\n").orElseThrow());
    }

    @Test
    void tokenIsDeterministicForTheSameParticipation() {
        String id = UUID.randomUUID().toString();
        assertEquals(service.tokenFor(id), service.tokenFor(id));
        assertNotEquals(service.tokenFor(id), service.tokenFor(UUID.randomUUID().toString()));
    }

    @Test
    void tamperedIdOrSignatureIsRejected() {
        String id = UUID.randomUUID().toString();
        String token = service.tokenFor(id);
        String other = UUID.randomUUID().toString();
        assertTrue(service.verify(token.replace(id, other)).isEmpty());

        String[] parts = token.split("\\.");
        String flipped = parts[2].substring(0, parts[2].length() - 1) + (parts[2].endsWith("A") ? "B" : "A");
        assertTrue(service.verify(parts[0] + "." + parts[1] + "." + flipped).isEmpty());
        assertTrue(service.verify(parts[0] + "." + parts[1] + ".").isEmpty());
        assertTrue(service.verify(parts[0] + "." + parts[1]).isEmpty());
    }

    @Test
    void wrongPrefixOrShapeIsRejected() {
        String id = UUID.randomUUID().toString();
        String token = service.tokenFor(id);
        assertTrue(service.verify(token.replace("HTATT1", "HTATT2")).isEmpty());
        assertTrue(service.verify(null).isEmpty());
        assertTrue(service.verify("").isEmpty());
        assertTrue(service.verify("   ").isEmpty());
        assertTrue(service.verify("https://example.com/certificates/ABC").isEmpty());
        assertTrue(service.verify(token + ".extra").isEmpty());
        assertTrue(service.verify("HTATT1..sig").isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String id = UUID.randomUUID().toString();
        AttendanceTokenService other = new AttendanceTokenService("another-secret-0123456789-abcdef");
        assertTrue(service.verify(other.tokenFor(id)).isEmpty());
    }

    @Test
    void refusesToSignInvalidParticipationIds() {
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor(null));
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor(" "));
        assertThrows(IllegalArgumentException.class, () -> service.tokenFor("a.b"));
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThrows(IllegalStateException.class, () -> new AttendanceTokenService(null));
        assertThrows(IllegalStateException.class, () -> new AttendanceTokenService(""));
        assertThrows(IllegalStateException.class, () -> new AttendanceTokenService("short"));
    }

    @Test
    void matrixEncodesTheToken() {
        BitMatrix matrix = service.matrixFor(UUID.randomUUID().toString());
        assertTrue(matrix.getWidth() >= 21);
        assertEquals(matrix.getWidth(), matrix.getHeight());
    }

    @Test
    void pngDecodesBackToTheSignedToken() throws Exception {
        String id = UUID.randomUUID().toString();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(service.pngFor(id, 8)));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        String decoded = new QRCodeReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)))).getText();
        assertEquals(service.tokenFor(id), decoded);
        assertEquals(id, service.verify(decoded).orElseThrow());
    }

    @Test
    void pngHasQuietZoneAroundTheCode() throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(service.pngFor(UUID.randomUUID().toString(), 4)));
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
