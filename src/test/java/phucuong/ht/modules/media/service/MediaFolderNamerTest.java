package phucuong.ht.modules.media.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFolderNamerTest {

    private final UUID id = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000000");

    @Test
    void removesVietnameseAccentsAndAppendsShortId() {
        assertEquals("phu-cuong-1a2b3c4d", MediaFolderNamer.dioceseFolder("Phú Cường", id));
        assertEquals("xuan-loc-1a2b3c4d", MediaFolderNamer.dioceseFolder("Xuân Lộc", id));
        assertEquals("dong-nai-1a2b3c4d", MediaFolderNamer.dioceseFolder("  ĐỒNG  NAI!! ", id));
    }

    @Test
    void emptyOrSymbolOnlyNameFallsBackToShortId() {
        assertEquals("1a2b3c4d", MediaFolderNamer.dioceseFolder("???", id));
        assertEquals("1a2b3c4d", MediaFolderNamer.dioceseFolder(null, id));
    }

    @Test
    void longNamesAreTruncated() {
        String folder = MediaFolderNamer.dioceseFolder("a".repeat(300), id);
        assertTrue(folder.length() <= 109);
        assertTrue(folder.endsWith("-1a2b3c4d"));
    }
}
