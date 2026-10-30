package phucuong.ht.common.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextFoldingTest {

    @Test
    void foldsVietnameseAccentsAndCase() {
        assertEquals("nguyen van dung", TextFolding.foldText("  Nguyễn Văn Đũng "));
        assertEquals("", TextFolding.foldText(null));
    }

    @Test
    void normalizeTreatsBlankAsNull() {
        assertNull(TextFolding.normalize("   "));
        assertNull(TextFolding.normalize(null));
        assertEquals("abc", TextFolding.normalize("  abc "));
    }

    @Test
    void readsFiltersAsTrimmedTextOrBoolean() {
        Map<String, Object> filters = Map.of("name", " mau ", "blank", " ", "isDefault", "true");

        assertEquals("mau", TextFolding.filterText(filters, "name"));
        assertNull(TextFolding.filterText(filters, "blank"));
        assertNull(TextFolding.filterText(filters, "missing"));
        assertEquals(Boolean.TRUE, TextFolding.filterBoolean(filters, "isDefault"));
        assertNull(TextFolding.filterBoolean(filters, "missing"));
    }

    @Test
    void uniqueNameAddsNumericSuffixCaseInsensitively() {
        List<String> taken = List.of("Mẫu A", "mẫu a (1)");

        assertEquals("mẫu a (2)", TextFolding.uniqueName(taken, "mẫu a", null, "Mặc định", 200));
        assertEquals("Mẫu B", TextFolding.uniqueName(taken, "Mẫu B", null, "Mặc định", 200));
    }

    @Test
    void uniqueNameIgnoresTheConfigBeingEdited() {
        assertEquals("Mẫu A", TextFolding.uniqueName(List.of("Mẫu A"), "Mẫu A", "mẫu a", "Mặc định", 200));
    }

    @Test
    void uniqueNameFallsBackToDefaultAndTruncates() {
        assertEquals("Mặc định", TextFolding.uniqueName(List.of(), "  ", null, "Mặc định", 200));
        assertEquals("abc", TextFolding.uniqueName(List.of(), "abcdef", null, "x", 3));
    }
}
