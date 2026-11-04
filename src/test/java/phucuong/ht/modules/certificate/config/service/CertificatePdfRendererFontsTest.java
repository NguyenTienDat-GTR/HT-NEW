package phucuong.ht.modules.certificate.config.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Mọi họ chữ trong trình thiết kế phải có file TTF và vẽ được tiếng Việt có dấu. */
class CertificatePdfRendererFontsTest {

    private static final String VIETNAMESE = "Đức Ông Phêrô Nguyễn Văn Ặ ữ ộ ẳ Ỹ";

    private static final List<String> FAMILIES = List.of(
            "sans", "serif", "bevietnam", "lobster", "pacifico", "patrickhand",
            "roboto", "montserrat", "lora", "playfair", "notoserif", "tinos", "dancing", "greatvibes");

    @Test
    void everyFamilyRendersVietnameseInAllStyles() throws Exception {
        CertificatePdfRenderer renderer = new CertificatePdfRenderer();
        for (String family : FAMILIES) {
            for (boolean bold : new boolean[]{false, true}) {
                for (boolean italic : new boolean[]{false, true}) {
                    var item = new CertificatePdfRenderer.RenderItem(VIETNAMESE, 0.5, 0.5, 0.05, "#000000", "center", family, bold, italic, false);
                    byte[] pdf = assertDoesNotThrow(
                            () -> renderer.renderPdf(List.of(new CertificatePdfRenderer.RenderPage(null, List.of(item), List.of(), List.of(), 842f, 595f))),
                            family + " bold=" + bold + " italic=" + italic);
                    try (PDDocument document = Loader.loadPDF(pdf)) {
                        assertEquals(1, document.getNumberOfPages());
                    }
                }
            }
        }
    }

    @Test
    void newFontFilesContainVietnameseGlyphs() throws Exception {
        List<String> files = List.of(
                "Roboto-Regular", "Roboto-Bold", "Roboto-Italic", "Roboto-BoldItalic",
                "Montserrat-Regular", "Montserrat-Bold", "Lora-Regular", "Lora-BoldItalic",
                "PlayfairDisplay-Regular", "PlayfairDisplay-BoldItalic", "NotoSerif-Regular", "NotoSerif-Bold",
                "Tinos-Regular", "Tinos-BoldItalic", "DancingScript-Regular", "DancingScript-Bold", "GreatVibes-Regular");
        try (PDDocument document = new PDDocument()) {
            for (String file : files) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("fonts/" + file + ".ttf")) {
                    assertNotNull(in, file);
                    PDType0Font font = PDType0Font.load(document, in);
                    assertDoesNotThrow(() -> font.encode(VIETNAMESE), file + " thiếu glyph tiếng Việt");
                }
            }
        }
    }
}
