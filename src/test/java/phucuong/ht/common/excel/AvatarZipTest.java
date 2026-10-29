package phucuong.ht.common.excel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarZipTest {

    @TempDir
    Path tempDir;

    @Test
    void ignoresTraversalAndHiddenMetadataWithoutExtractingAnything() throws Exception {
        Path zip = zip(Map.of("../outside.jpg", bytes(1), "__MACOSX/photo.jpg", bytes(2), ".hidden.png", bytes(3), "safe/photo.png", bytes(4)));
        try (AvatarZip images = new AvatarZip(zip, 1024)) {
            assertFalse(images.lookup("outside.jpg").found());
            assertFalse(images.lookup("photo.jpg").found());
            assertArrayEquals(bytes(4), images.read("photo.png"));
        }
        assertFalse(Files.exists(tempDir.resolve("outside.jpg")));
    }

    @Test
    void findsNamesCaseInsensitivelyAndCompletesSupportedExtension() throws Exception {
        Path zip = zip(Map.of("folder/AnhChanDung.JPEG", bytes(7)));
        try (AvatarZip images = new AvatarZip(zip, 1024)) {
            assertTrue(images.lookup("anhchandung").found());
            assertArrayEquals(bytes(7), images.read("ANHCHANDUNG.jpeg"));
        }
    }

    @Test
    void duplicateBaseNameInDifferentFoldersIsAmbiguous() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a/avatar.jpg", bytes(1));
        entries.put("b/AVATAR.JPG", bytes(2));
        try (AvatarZip images = new AvatarZip(zip(entries), 1024)) {
            AvatarZip.Lookup lookup = images.lookup("avatar.jpg");
            assertFalse(lookup.found());
            assertTrue(lookup.reason().contains("trùng"), lookup.reason());
        }
    }

    @Test
    void measuresActualBytesAndStopsOversizedImage() throws Exception {
        try (AvatarZip images = new AvatarZip(zip(Map.of("bomb.jpg", new byte[2_048])), 1_024)) {
            AvatarZip.Lookup lookup = images.lookup("bomb.jpg");
            assertFalse(lookup.found());
            assertTrue(lookup.reason().contains("vượt quá"), lookup.reason());
        }
    }

    private Path zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        Path path = tempDir.resolve("images-" + System.nanoTime() + ".zip");
        Files.write(path, output.toByteArray());
        return path;
    }

    private static byte[] bytes(int value) {
        return new byte[]{(byte) value};
    }
}
