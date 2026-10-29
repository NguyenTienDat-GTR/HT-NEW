package phucuong.ht.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Đọc ZIP ảnh trực tiếp theo entry, không giải nén ra đĩa. */
public final class AvatarZip implements AutoCloseable {

    public static final int MAX_ENTRIES = 5_000;
    public static final long MAX_TOTAL_BYTES = 300L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public record Lookup(String entryName, String reason) {
        public boolean found() {
            return entryName != null;
        }
    }

    private final ZipFile zip;
    private final long maxImageBytes;
    private final String unavailableReason;
    private final Map<String, List<ZipEntry>> byBaseName = new HashMap<>();
    private long bytesRead;

    public AvatarZip(Path path, long maxImageBytes) throws IOException {
        this.zip = new ZipFile(path.toFile());
        this.maxImageBytes = maxImageBytes;
        this.unavailableReason = null;
        try {
            index();
        } catch (IOException | RuntimeException exception) {
            zip.close();
            throw exception;
        }
    }

    private AvatarZip(long maxImageBytes, String unavailableReason) {
        this.zip = null;
        this.maxImageBytes = maxImageBytes;
        this.unavailableReason = unavailableReason;
    }

    public static AvatarZip unavailable(long maxImageBytes, String reason) {
        return new AvatarZip(maxImageBytes, reason);
    }

    private void index() throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        int count = 0;
        long declaredTotal = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (++count > MAX_ENTRIES) {
                throw new IOException("ZIP có quá 5.000 tệp");
            }
            String normalized = entry.getName().replace('\\', '/');
            if (unsafe(normalized)) {
                continue;
            }
            long size = entry.getSize();
            if (size > 0) {
                declaredTotal = Math.addExact(declaredTotal, size);
                if (declaredTotal > MAX_TOTAL_BYTES) {
                    throw new IOException("Tổng dung lượng giải nén của ZIP vượt quá 300MB");
                }
            }
            String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
            if (baseName.isBlank() || baseName.startsWith(".") || normalized.toLowerCase(Locale.ROOT).contains("/__macosx/")) {
                continue;
            }
            String extension = extension(baseName);
            if (!EXTENSIONS.contains(extension)) {
                continue;
            }
            byBaseName.computeIfAbsent(baseName.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(entry);
        }
    }

    private static boolean unsafe(String name) {
        if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*")) {
            return true;
        }
        for (String part : name.split("/")) {
            if (part.equals("..") || part.startsWith(".")) {
                return true;
            }
        }
        return name.equals("__MACOSX") || name.startsWith("__MACOSX/");
    }

    public Lookup lookup(String requestedName) {
        if (unavailableReason != null) {
            return new Lookup(null, unavailableReason);
        }
        if (requestedName == null || requestedName.isBlank()) {
            return new Lookup(null, "Tên file ảnh để trống");
        }
        String normalized = requestedName.trim().replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
        List<ZipEntry> matches = new ArrayList<>();
        if (extension(baseName).isEmpty()) {
            for (String extension : EXTENSIONS) {
                matches.addAll(byBaseName.getOrDefault((baseName + "." + extension).toLowerCase(Locale.ROOT), List.of()));
            }
        } else {
            matches.addAll(byBaseName.getOrDefault(baseName.toLowerCase(Locale.ROOT), List.of()));
        }
        if (matches.isEmpty()) {
            return new Lookup(null, "Không tìm thấy file ảnh trong ZIP");
        }
        if (matches.size() > 1) {
            return new Lookup(null, "Tên file ảnh bị trùng ở nhiều thư mục trong ZIP");
        }
        ZipEntry entry = matches.get(0);
        if (entry.getSize() > maxImageBytes) {
            return new Lookup(null, "Ảnh vượt quá " + maxImageBytes / (1024 * 1024) + "MB");
        }
        return new Lookup(entry.getName(), null);
    }

    public byte[] read(String requestedName) throws IOException {
        Lookup lookup = lookup(requestedName);
        if (!lookup.found()) {
            throw new IOException(lookup.reason());
        }
        ZipEntry entry = zip.getEntry(lookup.entryName());
        try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            long imageBytes = 0;
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read == 0) {
                    continue;
                }
                imageBytes += read;
                bytesRead += read;
                if (imageBytes > maxImageBytes) {
                    throw new IOException("Ảnh vượt quá " + maxImageBytes / (1024 * 1024) + "MB");
                }
                if (bytesRead > MAX_TOTAL_BYTES) {
                    throw new IOException("Tổng dung lượng giải nén của ZIP vượt quá 300MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
        }
    }
}
