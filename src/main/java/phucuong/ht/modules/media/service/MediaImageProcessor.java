package phucuong.ht.modules.media.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.model.enums.MediaVariants;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/**
 * Kiểm tra và tối ưu ảnh tải lên. Việc tạo biến thể do backend tự làm (không dùng biến đổi của nhà cung cấp)
 * để mọi nhà cung cấp lưu đúng cùng một tập tệp, nên chuyển nhà cung cấp chỉ là sao chép byte.
 * <ul>
 *   <li>Chỉ nhận JPEG/PNG/WebP (kiểm tra magic bytes + giải mã thật), chặn ảnh quá nhiều điểm ảnh.</li>
 *   <li>Tự xoay theo EXIF rồi mã hóa lại → loại bỏ EXIF/GPS.</li>
 *   <li>Avatar khổ dọc 4x6 (2:3, dùng in lên chứng nhận / thẻ): thumb 120x180, medium 480x720 (~300dpi cho ảnh rộng 4cm).
 *       Nền: master tối đa 3508px (A4 300dpi, cho PDF), preview 1600px.</li>
 * </ul>
 */
@Component
public class MediaImageProcessor {

    public static final String CONTENT_TYPE = "image/jpeg";
    public static final String EXTENSION = "jpg";

    // Avatar tỉ lệ 4x6 (rộng:cao = 2:3).
    private static final int THUMB_WIDTH = 120;
    private static final int THUMB_HEIGHT = 180;
    private static final int MEDIUM_WIDTH = 480;
    private static final int MEDIUM_HEIGHT = 720;
    private static final int MASTER_MAX_EDGE = 3508;
    private static final int PREVIEW_MAX_EDGE = 1600;

    private final MediaProperties properties;

    public MediaImageProcessor(MediaProperties properties) {
        this.properties = properties;
    }

    public record Processed(String name, byte[] data, int width, int height, String sha256) {
    }

    public List<Processed> process(byte[] input, MediaCategory category) {
        if (input == null || input.length == 0) {
            throw invalid("Tệp ảnh trống");
        }
        long maxBytes = category.isAvatar() ? properties.getMaxAvatarBytes() : properties.getMaxBackgroundBytes();
        if (input.length > maxBytes) {
            throw invalid("Ảnh quá lớn (tối đa " + (maxBytes / 1024 / 1024) + " MB)");
        }
        assertSupportedFormat(input);
        assertPixelLimit(input);

        BufferedImage source = decode(input);
        List<Processed> result = new ArrayList<>();
        for (String variant : category.getVariants()) {
            BufferedImage scaled = switch (variant) {
                case MediaVariants.THUMB -> portraitCrop(source, THUMB_WIDTH, THUMB_HEIGHT);
                case MediaVariants.MEDIUM -> portraitCrop(source, MEDIUM_WIDTH, MEDIUM_HEIGHT);
                case MediaVariants.MASTER -> fitWithin(source, MASTER_MAX_EDGE);
                case MediaVariants.PREVIEW -> fitWithin(source, PREVIEW_MAX_EDGE);
                default -> throw new IllegalStateException("Biến thể không hỗ trợ: " + variant);
            };
            float quality = switch (variant) {
                case MediaVariants.THUMB -> 0.80f;
                case MediaVariants.MEDIUM -> 0.82f;
                case MediaVariants.MASTER -> 0.90f;
                default -> 0.80f;
            };
            byte[] bytes = encodeJpeg(scaled, quality);
            result.add(new Processed(variant, bytes, scaled.getWidth(), scaled.getHeight(), sha256(bytes)));
        }
        return result;
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertSupportedFormat(byte[] b) {
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        boolean gif = b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
        if (!jpeg && !png && !webp && !gif) {
            throw invalid("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP");
        }
    }

    private void assertPixelLimit(byte[] input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw invalid("Không đọc được ảnh, tệp có thể bị hỏng");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > properties.getMaxPixels()) {
                    throw invalid("Kích thước ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid("Không đọc được ảnh, tệp có thể bị hỏng");
        }
    }

    private BufferedImage decode(byte[] input) {
        try {
            // Thumbnailator đọc EXIF orientation và xoay ảnh về đúng chiều.
            BufferedImage image = Thumbnails.of(new ByteArrayInputStream(input)).scale(1.0).asBufferedImage();
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                throw invalid("Không đọc được ảnh, tệp có thể bị hỏng");
            }
            return image;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid("Không đọc được ảnh, tệp có thể bị hỏng");
        }
    }

    /**
     * Cắt chính giữa theo đúng tỉ lệ targetWidth:targetHeight (phòng khi ảnh gửi lên chưa đúng khổ) rồi thu nhỏ.
     * Không phóng to: ảnh nhỏ hơn khổ đích giữ nguyên độ phân giải.
     */
    private BufferedImage portraitCrop(BufferedImage source, int targetWidth, int targetHeight) {
        double targetRatio = (double) targetWidth / targetHeight;
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if ((double) cropWidth / cropHeight > targetRatio) {
            cropWidth = Math.max(1, (int) Math.round(cropHeight * targetRatio));
        } else {
            cropHeight = Math.max(1, (int) Math.round(cropWidth / targetRatio));
        }
        int x = (source.getWidth() - cropWidth) / 2;
        int y = (source.getHeight() - cropHeight) / 2;
        BufferedImage cropped = source.getSubimage(x, y, cropWidth, cropHeight);
        if (cropWidth <= targetWidth && cropHeight <= targetHeight) {
            return cropped;
        }
        return resize(cropped, targetWidth, targetHeight);
    }

    private BufferedImage fitWithin(BufferedImage source, int maxEdge) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= maxEdge) {
            return source;
        }
        return resize(source, maxEdge, maxEdge);
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        try {
            return Thumbnails.of(source).size(width, height).asBufferedImage();
        } catch (IOException e) {
            throw invalid("Không xử lý được ảnh");
        }
    }

    /** JPEG không có kênh alpha: trải ảnh trong suốt lên nền trắng rồi mã hóa. */
    private byte[] encodeJpeg(BufferedImage image, float quality) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(rgb, null, null), param);
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw invalid("Không nén được ảnh");
        } finally {
            writer.dispose();
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }
}
