package phucuong.ht.modules.training.registrationTemplate.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Nén / giải nén file mẫu: trên Cloudinary chỉ lưu bản nén (gzip mức cao nhất) cho đỡ dung lượng; khi mở sửa hoặc xuất
 * thì giải nén ra đúng file gốc (từng byte) rồi mới dùng. Hệ thống không giữ thêm bản gốc ở đâu khác.
 */
final class TemplateCompression {

    /** Giới hạn dung lượng sau giải nén, chặn file nén bất thường. */
    static final long MAX_ORIGINAL_BYTES = 20L * 1024 * 1024;

    private TemplateCompression() {
    }

    static byte[] compress(byte[] original) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, original.length / 2))) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out) {
                {
                    def.setLevel(Deflater.BEST_COMPRESSION);
                }
            }) {
                gzip.write(original);
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không nén được file mẫu", exception);
        }
    }

    static byte[] decompress(byte[] compressed) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                total += read;
                if (total > MAX_ORIGINAL_BYTES) {
                    throw new IOException("File sau giải nén quá lớn");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không giải nén được file mẫu", exception);
        }
    }
}
