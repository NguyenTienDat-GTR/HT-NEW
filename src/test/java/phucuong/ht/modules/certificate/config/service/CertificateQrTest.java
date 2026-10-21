package phucuong.ht.modules.certificate.config.service;

import com.google.zxing.common.BitMatrix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateQrTest {

    private final CertificateQrService qrService = new CertificateQrService(Runnable::run, "https://ht.example.com/");

    @Test
    void generatesMatrixAndSvgForCertificateCode() {
        BitMatrix matrix = qrService.generate("GPPC-HTI-2026-1");
        assertTrue(matrix.getWidth() >= 21);
        assertTrue(qrService.toSvgDataUrl(matrix).startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    void qrContentIsDetailPageLink() {
        assertEquals("https://ht.example.com/certificates/GPPC-HTI-2026-1", qrService.contentFor(" GPPC-HTI-2026-1 "));
        assertEquals("https://ht.example.com/certificates/A%20B", qrService.contentFor("A B"));
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> qrService.generate(" "));
    }

    @Test
    void rendersPdfWithQr() {
        BitMatrix matrix = qrService.generate("GPPC-HTI-2026-1");
        byte[] pdf = new CertificatePdfRenderer().renderPdf(List.of(new CertificatePdfRenderer.RenderPage(
                null, List.of(), List.of(new CertificatePdfRenderer.RenderQr(matrix, 0.8, 0.8, 0.15)), 842f, 595f)));
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
