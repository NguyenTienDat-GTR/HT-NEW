package phucuong.ht.modules.media.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import phucuong.ht.modules.media.config.MediaProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thư mục: xóa chỉ khi trống; liệt kê tệp / thư mục phục vụ lệnh quét dọn. */
class LocalFileStorageProviderSweepTest {

    @TempDir
    Path dir;

    private LocalFileStorageProvider provider() {
        MediaProperties properties = new MediaProperties();
        properties.getLocal().setDir(dir.toString());
        return new LocalFileStorageProvider(properties);
    }

    @Test
    void deleteFolderOnlyRemovesEmptyFolders() {
        LocalFileStorageProvider provider = provider();
        provider.put("a/b/thumb.jpg", new byte[]{1}, "image/jpeg");

        assertFalse(provider.deleteFolder("a/b"), "thư mục còn tệp phải được giữ");
        assertTrue(Files.exists(dir.resolve("a/b/thumb.jpg")));

        provider.delete("a/b/thumb.jpg");
        assertTrue(provider.deleteFolder("a/b"));
        assertFalse(Files.exists(dir.resolve("a/b")));
        assertTrue(Files.exists(dir.resolve("a")), "chỉ xóa đúng thư mục được yêu cầu");
        assertFalse(provider.deleteFolder("a/b"), "thư mục không tồn tại không phải lỗi");
    }

    @Test
    void deleteFolderNeverTouchesRootOrEscapes() {
        LocalFileStorageProvider provider = provider();
        assertFalse(provider.deleteFolder(""));
        assertFalse(provider.deleteFolder("../outside"));
        assertTrue(Files.exists(dir));
    }

    @Test
    void listsFilesUnderPrefixAndSkipsTemporaryUploads() throws Exception {
        LocalFileStorageProvider provider = provider();
        provider.put("root/pending/avatars/x/thumb.jpg", new byte[]{1}, "image/jpeg");
        provider.put("root/pending/avatars/y/medium.jpg", new byte[]{2}, "image/jpeg");
        provider.put("root/dioceses/d/avatars/z/thumb.jpg", new byte[]{3}, "image/jpeg");
        Files.write(dir.resolve("root/pending/avatars/y/.upload-123.tmp"), new byte[]{9});

        List<String> keys = new ArrayList<>();
        provider.forEachFile("root/pending/", file -> keys.add(file.key()));
        assertEquals(List.of("root/pending/avatars/x/thumb.jpg", "root/pending/avatars/y/medium.jpg"),
                keys.stream().sorted().toList());

        List<String> first = new ArrayList<>();
        provider.forEachFile("root/pending/", file -> {
            first.add(file.key());
            return false;
        });
        assertEquals(1, first.size(), "trả false thì dừng duyệt");

        List<String> none = new ArrayList<>();
        provider.forEachFile("root/missing/", file -> none.add(file.key()));
        assertTrue(none.isEmpty());
    }

    @Test
    void listsDirectChildFoldersOnly() {
        LocalFileStorageProvider provider = provider();
        provider.put("root/a/1/thumb.jpg", new byte[]{1}, "image/jpeg");
        provider.ensureFolder("root/b");

        assertEquals(List.of("a", "b"), provider.listFolders("root"));
        assertEquals(List.of("1"), provider.listFolders("root/a"));
        assertTrue(provider.listFolders("root/none").isEmpty());
    }
}
