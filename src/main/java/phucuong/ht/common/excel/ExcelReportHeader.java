package phucuong.ht.common.excel;

import java.util.List;

/**
 * Phần đầu của file xuất: các dòng đơn vị (GIÁO PHẬN ..., GIÁO HẠT ..., GIÁO XỨ ...) rồi dòng tiêu đề nội dung
 * (vd. DANH SÁCH THAM DỰ KHÓA HUYNH TRƯỞNG CẤP I 2026). Các dòng được gộp ô theo số cột và căn giữa.
 */
public record ExcelReportHeader(List<String> unitLines, String title) {

    public ExcelReportHeader {
        unitLines = unitLines == null ? List.of() : unitLines.stream().filter(line -> line != null && !line.isBlank()).toList();
    }

    public static ExcelReportHeader titleOnly(String title) {
        return new ExcelReportHeader(List.of(), title);
    }

    public boolean isEmpty() {
        return unitLines.isEmpty() && (title == null || title.isBlank());
    }
}
