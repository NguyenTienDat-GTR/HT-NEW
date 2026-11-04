package phucuong.ht.modules.certificate.config.service;

import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mã QR và ảnh mẫu của PDF xem trước phải vẽ được bằng bộ vẽ thật. */
class PreviewSamplesTest {

    @Test
    void sampleQrAndPhotoRenderIntoAPdf() throws Exception {
        BitMatrix qr = PreviewSamples.qr("GPPC-DT-2026-14");
        assertTrue(qr.getWidth() > 0);
        byte[] photo = PreviewSamples.photoPng();
        assertTrue(photo.length > 0);

        byte[] pdf = new CertificatePdfRenderer().renderPdf(List.of(new CertificatePdfRenderer.RenderPage(
                null,
                List.of(new CertificatePdfRenderer.RenderItem("Đức Ông Phêrô", 0.5, 0.4, 0.05, "#000000", "center", "roboto", true, false, false)),
                List.of(new CertificatePdfRenderer.RenderQr(qr, 0.8, 0.8, 0.15)),
                List.of(new CertificatePdfRenderer.RenderPhoto(photo, 0.2, 0.4, 0.3, 2.0 / 3.0)),
                842f, 595f)));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
        }
    }
}
