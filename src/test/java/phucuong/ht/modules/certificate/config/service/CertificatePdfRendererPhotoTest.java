package phucuong.ht.modules.certificate.config.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.badge.config.service.BadgePdfRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ảnh huynh trưởng được vẽ lên chứng nhận và thẻ; không có ảnh thì không vẽ gì. */
class CertificatePdfRendererPhotoTest {

    private static byte[] jpeg() throws IOException {
        BufferedImage image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 60, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static int imageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDResources resources = document.getPage(0).getResources();
            int count = 0;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Test
    void certificateDrawsEachPhotoItem() throws Exception {
        CertificatePdfRenderer renderer = new CertificatePdfRenderer();
        byte[] photo = jpeg();
        byte[] withPhoto = renderer.renderPdf(List.of(new CertificatePdfRenderer.RenderPage(
                null, List.of(), List.of(), List.of(new CertificatePdfRenderer.RenderPhoto(photo, 0.2, 0.3, 0.25)), 842f, 595f)));
        assertEquals(1, imageCount(withPhoto));

        byte[] without = renderer.renderPdf(List.of(new CertificatePdfRenderer.RenderPage(null, List.of(), List.of(), List.of(), 842f, 595f)));
        assertEquals(0, imageCount(without));
    }

    @Test
    void badgeDrawsThePhotoOfEachCard() throws Exception {
        BadgePdfRenderer renderer = new BadgePdfRenderer(new CertificatePdfRenderer());
        byte[] photo = jpeg();
        var withPhoto = new BadgePdfRenderer.Side(null, List.of(), List.of(), List.of(new CertificatePdfRenderer.RenderPhoto(photo, 0.3, 0.5, 0.6)));
        var withoutPhoto = new BadgePdfRenderer.Side(null, List.of(), List.of());
        BadgePdfRenderer.Sheet sheet = new BadgePdfRenderer.Sheet(210, 297, 90, 55, false, List.of(
                new BadgePdfRenderer.Placed(withPhoto, 10, 10), new BadgePdfRenderer.Placed(withoutPhoto, 10, 80)));
        assertEquals(1, imageCount(renderer.render(List.of(sheet))));
    }

    @Test
    void layoutItemResolvesPhotoAspectFromRatio() {
        var item = new phucuong.ht.modules.certificate.config.model.CertificateLayoutItem();
        assertEquals(2.0 / 3.0, item.photoAspect(), 1e-9, "mặc định 4x6");
        item.setPhotoRatio("4x6");
        assertEquals(2.0 / 3.0, item.photoAspect(), 1e-9);
        item.setPhotoRatio("3x4");
        assertEquals(0.75, item.photoAspect(), 1e-9);
        item.setPhotoRatio("free");
        item.setSize(0.4);
        item.setPhotoWidth(0.2);
        assertEquals(0.5, item.photoAspect(), 1e-9);
        item.setPhotoWidth(null);
        assertEquals(2.0 / 3.0, item.photoAspect(), 1e-9, "tự do nhưng thiếu chiều rộng thì lấy 4x6");
        item.setPhotoWidth(100.0);
        assertEquals(3.0, item.photoAspect(), 1e-9, "giới hạn tỉ lệ để không méo quá mức");
    }

    @Test
    void photoIsCoverCroppedIntoItsBoxWithoutDistortion() throws Exception {
        CertificatePdfRenderer renderer = new CertificatePdfRenderer();
        // Ảnh ngang 120x60 vào ô dọc 3x4: phải vẽ phủ kín ô (cắt phần thừa) chứ không kéo méo.
        BufferedImage wide = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(wide, "jpg", out);
        byte[] pdf = renderer.renderPdf(List.of(new CertificatePdfRenderer.RenderPage(
                null, List.of(), List.of(),
                List.of(new CertificatePdfRenderer.RenderPhoto(out.toByteArray(), 0.5, 0.5, 0.4, 0.75)), 842f, 595f)));
        assertEquals(1, imageCount(pdf));
    }
}
