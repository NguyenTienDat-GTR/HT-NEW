package phucuong.ht.excel;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.excel.ExcelRowOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelRowOrderTest {

    private record Row(String deanery, String parish, String fullName) {
    }

    private static List<String> sorted(List<Row> rows) {
        List<Row> copy = new ArrayList<>(rows);
        copy.sort(ExcelRowOrder.byText(Row::deanery, Row::parish, row -> ExcelRowOrder.givenName(row.fullName())));
        return copy.stream().map(Row::fullName).toList();
    }

    @Test
    void sortsByDeaneryThenParishThenGivenNameNotSurname() {
        List<String> order = sorted(List.of(
                new Row("Gò Vấp", "Bình Hòa", "Nguyễn Văn Bình"),
                new Row("Bình Thạnh", "Xóm Mới", "Trần Văn An"),
                new Row("Bình Thạnh", "An Phú", "Lê Thị Dũng"),
                new Row("Bình Thạnh", "An Phú", "Phạm Văn Đạt"),
                new Row("Bình Thạnh", "An Phú", "Vũ Hoàng An")));
        // Giáo hạt Bình Thạnh trước Gò Vấp; trong giáo xứ An Phú: An, Dũng, Đạt (xếp theo tên, không theo họ).
        assertEquals(List.of("Vũ Hoàng An", "Lê Thị Dũng", "Phạm Văn Đạt", "Trần Văn An", "Nguyễn Văn Bình"), order);
    }

    @Test
    void usesVietnameseAlphabetAndPutsBlanksLast() {
        List<String> order = sorted(List.of(
                new Row(null, null, "Hà Văn Z"),
                new Row("Đồng Nai", "A", "Ng Ơ"),
                new Row("Đại Nghĩa", "A", "Ng Ô"),
                new Row("Dương Đông", "A", "Ng E")));
        // Dương Đông < Đại Nghĩa < Đồng Nai (D trước Đ), trống xếp cuối.
        assertEquals(List.of("Ng E", "Ng Ô", "Ng Ơ", "Hà Văn Z"), order);
    }

    @Test
    void givenNameIsTheLastWord() {
        assertEquals("An", ExcelRowOrder.givenName("Nguyễn Văn An"));
        assertEquals("An", ExcelRowOrder.givenName("  An "));
        assertEquals(null, ExcelRowOrder.givenName(null));
    }
}
