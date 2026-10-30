package phucuong.ht.modules.media.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.model.enums.MediaVariants;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaImageProcessorTest {

    private final MediaProperties properties = new MediaProperties();
    private final MediaImageProcessor processor = new MediaImageProcessor(properties);

    private static byte[] image(int width, int height, String format, int imageType) throws IOException {
        BufferedImage img = new BufferedImage(width, height, imageType);
        Graphics2D g = img.createGraphics();
        // Ảnh có nhiều chi tiết để kích thước nén có ý nghĩa.
        for (int i = 0; i < width; i += 8) {
            g.setColor(new Color((i * 7) % 256, (i * 13) % 256, (i * 29) % 256));
            g.fillRect(i, 0, 8, height);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static Map<String, MediaImageProcessor.Processed> byName(List<MediaImageProcessor.Processed> list) {
        return list.stream().collect(Collectors.toMap(MediaImageProcessor.Processed::name, p -> p));
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    @Test
    void avatarIsCroppedToPortrait4x6WithSmallThumbAndMedium() throws IOException {
        byte[] source = image(1200, 800, "png", BufferedImage.TYPE_INT_RGB);
        Map<String, MediaImageProcessor.Processed> variants = byName(processor.process(source, MediaCategory.LEADER_AVATAR));

        assertEquals(2, variants.size());
        MediaImageProcessor.Processed thumb = variants.get(MediaVariants.THUMB);
        MediaImageProcessor.Processed medium = variants.get(MediaVariants.MEDIUM);
        // Khổ dọc 4x6 (rộng:cao = 2:3).
        assertTrue(Math.abs(thumb.width() - 120) <= 1 && Math.abs(thumb.height() - 180) <= 1, thumb.width() + "x" + thumb.height());
        assertTrue(Math.abs(medium.width() - 480) <= 1 && Math.abs(medium.height() - 720) <= 1, medium.width() + "x" + medium.height());
        assertTrue(thumb.data().length < 15_000, "thumb phải rất nhẹ: " + thumb.data().length);
        assertTrue(medium.data().length < 120_000, "bản medium phải nhẹ: " + medium.data().length);
        assertEquals(thumb.width(), decode(thumb.data()).getWidth());
        assertNotNull(thumb.sha256());
    }

    @Test
    void alreadyPortraitAvatarKeepsItsRatio() throws IOException {
        byte[] source = image(800, 1200, "png", BufferedImage.TYPE_INT_RGB);
        MediaImageProcessor.Processed medium = byName(processor.process(source, MediaCategory.LEADER_AVATAR)).get(MediaVariants.MEDIUM);
        assertEquals(480, medium.width());
        assertEquals(720, medium.height());
    }

    @Test
    void smallAvatarIsNotUpscaled() throws IOException {
        byte[] source = image(50, 70, "png", BufferedImage.TYPE_INT_RGB);
        Map<String, MediaImageProcessor.Processed> variants = byName(processor.process(source, MediaCategory.LEADER_AVATAR));
        assertTrue(variants.get(MediaVariants.THUMB).width() <= 50 && variants.get(MediaVariants.THUMB).height() <= 70);
        assertTrue(variants.get(MediaVariants.MEDIUM).height() <= 70);
    }

    @Test
    void backgroundKeepsAspectRatioAndCapsLongEdge() throws IOException {
        byte[] source = image(5000, 3500, "jpg", BufferedImage.TYPE_INT_RGB);
        Map<String, MediaImageProcessor.Processed> variants = byName(processor.process(source, MediaCategory.CERTIFICATE_BACKGROUND));

        MediaImageProcessor.Processed master = variants.get(MediaVariants.MASTER);
        MediaImageProcessor.Processed preview = variants.get(MediaVariants.PREVIEW);
        assertEquals(3508, master.width());
        assertEquals(1600, preview.width());
        // Giữ tỉ lệ 5000:3500 (sai số làm tròn ±1 điểm ảnh).
        assertTrue(Math.abs(master.height() - 3508 * 3500 / 5000) <= 1);
        assertTrue(Math.abs(preview.height() - 1600 * 3500 / 5000) <= 1);
        assertTrue(preview.data().length < master.data().length);
    }

    @Test
    void smallBackgroundIsKeptAtOriginalSize() throws IOException {
        byte[] source = image(800, 600, "png", BufferedImage.TYPE_INT_RGB);
        Map<String, MediaImageProcessor.Processed> variants = byName(processor.process(source, MediaCategory.BADGE_BACKGROUND));
        assertEquals(800, variants.get(MediaVariants.MASTER).width());
        assertEquals(600, variants.get(MediaVariants.MASTER).height());
    }

    @Test
    void transparentPngIsFlattenedOntoWhite() throws IOException {
        byte[] source = image(200, 200, "png", BufferedImage.TYPE_INT_ARGB);
        BufferedImage transparent = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", out);
        byte[] result = byName(processor.process(out.toByteArray(), MediaCategory.LEADER_AVATAR)).get(MediaVariants.MEDIUM).data();
        int rgb = decode(result).getRGB(100, 100) & 0xFFFFFF;
        assertTrue((rgb >> 16 & 0xFF) > 240 && (rgb >> 8 & 0xFF) > 240 && (rgb & 0xFF) > 240, "nền trong suốt phải thành trắng");
        assertNotNull(source);
    }

    @Test
    void rejectsFilesThatAreNotImagesEvenWithImageName() {
        byte[] fake = "<?php echo 'not an image'; ?>".getBytes(StandardCharsets.UTF_8);
        assertThrows(ApiException.class, () -> processor.process(fake, MediaCategory.LEADER_AVATAR));
        assertThrows(ApiException.class, () -> processor.process(new byte[0], MediaCategory.LEADER_AVATAR));
    }

    @Test
    void rejectsCorruptImageWithValidMagicBytes() {
        byte[] corrupt = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        assertThrows(ApiException.class, () -> processor.process(corrupt, MediaCategory.LEADER_AVATAR));
    }

    @Test
    void rejectsImagesWithTooManyPixels() throws IOException {
        properties.setMaxPixels(10_000);
        byte[] source = image(300, 300, "png", BufferedImage.TYPE_INT_RGB);
        ApiException exception = assertThrows(ApiException.class, () -> processor.process(source, MediaCategory.LEADER_AVATAR));
        assertTrue(exception.getMessage().contains("quá lớn"));
    }

    @Test
    void rejectsFilesOverTheByteLimit() throws IOException {
        properties.setMaxAvatarBytes(1_000);
        byte[] source = image(400, 400, "png", BufferedImage.TYPE_INT_RGB);
        assertThrows(ApiException.class, () -> processor.process(source, MediaCategory.LEADER_AVATAR));
    }
}
