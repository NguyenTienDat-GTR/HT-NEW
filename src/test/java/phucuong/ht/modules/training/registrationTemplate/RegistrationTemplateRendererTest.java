package phucuong.ht.modules.training.registrationTemplate;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.registrationTemplate.service.RegistrationTemplateRenderer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationTemplateRendererTest {

    private static final DataFormatter FORMAT = new DataFormatter();

    /** Mẫu giống file mẫu thật: tiêu đề, xứ đoàn, dòng tiêu đề cột, MỘT dòng dữ liệu chứa trường, rồi câu xác nhận gộp ô bên dưới. */
    private static byte[] template() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Mẫu");
            sheet.createRow(0).createCell(0).setCellValue("DANH SÁCH THAM DỰ {{tenKhoa|upper}}");
            sheet.createRow(1).createCell(0).setCellValue("{{capDonVi}}: {{tenDonVi}}");
            sheet.getRow(1).createCell(3).setCellValue("Giáo xứ: {{tenGiaoXu}}");
            Row header = sheet.createRow(3);
            String[] heads = {"STT", "Tên thánh", "Họ và tên", "Ngày sinh"};
            for (int c = 0; c < heads.length; c++) {
                header.createCell(c).setCellValue(heads[c]);
            }
            CellStyle border = workbook.createCellStyle();
            border.setBorderBottom(BorderStyle.THIN);
            Row data = sheet.createRow(4);
            String[] tokens = {"{{stt}}", "{{tenThanh}}", "{{hoVaTen}}", "{{ngaySinh}}"};
            for (int c = 0; c < tokens.length; c++) {
                data.createCell(c).setCellValue(tokens[c]);
                data.getCell(c).setCellStyle(border);
            }
            sheet.createRow(6).createCell(0).setCellValue("Danh sách này đã được xem xét");
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 0, 3));
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static RegistrationTemplateRenderer.Group group(String name, String parish, int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("tenThanh", "Phêrô");
            row.put("hoVaTen", parish + " " + i);
            row.put("ngaySinh", "01/02/2000");
            rows.add(row);
        }
        return new RegistrationTemplateRenderer.Group(name, Map.of("tenGiaoXu", parish), rows);
    }

    private static String text(Sheet sheet, int row, int col) {
        return sheet.getRow(row) == null || sheet.getRow(row).getCell(col) == null ? "" : FORMAT.formatCellValue(sheet.getRow(row).getCell(col));
    }

    private static final Map<String, String> COMMON = Map.of("tenKhoa", "Khóa cấp II 2027", "capDonVi", "Liên đoàn", "tenDonVi", "Phêrô");

    /** Ô có phần chữ đậm / nghiêng (rich text): thay {{trường}} xong vẫn giữ đúng định dạng từng đoạn, cả ở dòng dữ liệu được nhân bản. */
    @Test
    void keepsPartialFormattingWhenTokensAreReplaced() throws IOException {
        byte[] template;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Mẫu");
            XSSFFont bold = workbook.createFont();
            bold.setBold(true);
            XSSFFont italic = workbook.createFont();
            italic.setItalic(true);
            // "Giáo xứ: " thường, "{{tenGiaoXu}}" đậm, " (ghi chú)" nghiêng.
            XSSFRichTextString title = new XSSFRichTextString("Giáo xứ: {{tenGiaoXu}} (ghi chú)");
            title.applyFont(9, 22, bold);
            title.applyFont(22, 31, italic);
            sheet.createRow(0).createCell(0).setCellValue(title);
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("STT");
            header.createCell(1).setCellValue("Họ và tên");
            Row data = sheet.createRow(3);
            data.createCell(0).setCellValue("{{stt}}");
            XSSFRichTextString name = new XSSFRichTextString("Anh {{hoVaTen}}");
            name.applyFont(4, 15, bold);
            data.createCell(1).setCellValue(name);
            workbook.write(out);
            template = out.toByteArray();
        }

        byte[] out = RegistrationTemplateRenderer.renderWorkbook(template, Map.of(), List.of(group("Giáo xứ Phú Hòa", "Phú Hòa", 3)),
                RegistrationTemplateRenderer.Layout.SINGLE_SHEET, false);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            XSSFRichTextString title = sheet.getRow(0).getCell(0).getRichStringCellValue();
            assertEquals("Giáo xứ: Phú Hòa (ghi chú)", title.getString());
            assertEquals(false, runIsBold(title, 0));
            assertEquals(true, runIsBold(title, 9));
            assertEquals(true, runIsBold(title, 15));
            assertEquals(false, runIsBold(title, 16));
            assertEquals(true, runIsItalic(title, 18));
            for (int row = 3; row <= 5; row++) {
                XSSFRichTextString cell = sheet.getRow(row).getCell(1).getRichStringCellValue();
                assertEquals("Anh Phú Hòa " + (row - 2), cell.getString());
                assertEquals(false, runIsBold(cell, 0));
                assertEquals(true, runIsBold(cell, 4));
                assertEquals(true, runIsBold(cell, cell.length() - 1));
            }
        }
    }

    private static boolean runIsBold(XSSFRichTextString text, int index) {
        XSSFFont font = fontAt(text, index);
        return font != null && font.getBold();
    }

    private static boolean runIsItalic(XSSFRichTextString text, int index) {
        XSSFFont font = fontAt(text, index);
        return font != null && font.getItalic();
    }

    private static XSSFFont fontAt(XSSFRichTextString text, int index) {
        for (int i = 0; i < text.numFormattingRuns(); i++) {
            int start = text.getIndexOfFormattingRun(i);
            if (index >= start && index < start + text.getLengthOfFormattingRun(i)) {
                return text.getFontOfFormattingRun(i);
            }
        }
        return null;
    }

    @Test
    void multipliesTheTokenRowAndPushesFooterAndItsMergeDown() throws IOException {
        byte[] out = RegistrationTemplateRenderer.renderWorkbook(template(), COMMON, List.of(group("Giáo xứ Phú Hòa", "Phú Hòa", 5)),
                RegistrationTemplateRenderer.Layout.SINGLE_SHEET, false);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("DANH SÁCH THAM DỰ KHÓA CẤP II 2027", text(sheet, 0, 0));
            assertEquals("Liên đoàn: Phêrô", text(sheet, 1, 0));
            assertEquals("Giáo xứ: Phú Hòa", text(sheet, 1, 3));
            assertEquals("1", text(sheet, 4, 0));
            assertEquals("5", text(sheet, 8, 0));
            assertEquals("Phú Hòa 5", text(sheet, 8, 2));
            assertEquals("Danh sách này đã được xem xét", text(sheet, 10, 0));
            assertTrue(sheet.getMergedRegions().stream().anyMatch(region -> region.getFirstRow() == 10 && region.getLastColumn() == 3));
            assertFalse(sheet.getMergedRegions().stream().anyMatch(region -> region.getFirstRow() == 6));
            assertEquals(BorderStyle.THIN, sheet.getRow(8).getCell(2).getCellStyle().getBorderBottom());
        }
    }

    @Test
    void groupsInOneSheetWithTitles() throws IOException {
        byte[] out = RegistrationTemplateRenderer.renderWorkbook(template(), COMMON,
                List.of(group("Giáo xứ A", "A", 2), group("Giáo xứ B", "B", 3)), RegistrationTemplateRenderer.Layout.SINGLE_SHEET, true);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Giáo xứ A (2)", text(sheet, 4, 0));
            assertEquals("1", text(sheet, 5, 0));
            assertEquals("Giáo xứ B (3)", text(sheet, 7, 0));
            assertEquals("3", text(sheet, 10, 0));
            assertEquals("Giáo xứ: ", text(sheet, 1, 3));
        }
    }

    @Test
    void sheetPerGroupAndZip() throws IOException {
        List<RegistrationTemplateRenderer.Group> groups = List.of(group("Giáo xứ A", "A", 2), group("Giáo xứ B", "B", 4));
        byte[] out = RegistrationTemplateRenderer.renderWorkbook(template(), COMMON, groups, RegistrationTemplateRenderer.Layout.SHEET_PER_GROUP, false);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Giáo xứ A", workbook.getSheetName(0));
            assertEquals("Giáo xứ: B", text(workbook.getSheetAt(1), 1, 3));
            assertEquals("B 4", text(workbook.getSheetAt(1), 7, 2));
        }
        byte[] zip = RegistrationTemplateRenderer.renderZip(template(), COMMON, groups, "danh-sach");
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertEquals(List.of("danh-sach-giao-xu-a.xlsx", "danh-sach-giao-xu-b.xlsx"), names);
    }

    @Test
    void upperAndLowerModifiersChangeFieldCase() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("x");
            sheet.createRow(0).createCell(0).setCellValue("Giáo xứ {{tenGiaoXu|upper}} / {{tenGiaoXu|lower}} / {{tenGiaoXu}} / {{tenGiaoXu|title}}");
            sheet.createRow(1).createCell(0).setCellValue("{{hoVaTen|upper}}");
            workbook.write(out);
            byte[] result = RegistrationTemplateRenderer.renderWorkbook(out.toByteArray(), Map.of(), List.of(group("Giáo xứ Phú Hòa", "Phú Hòa", 1)),
                    RegistrationTemplateRenderer.Layout.SINGLE_SHEET, false);
            try (XSSFWorkbook filled = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertEquals("Giáo xứ PHÚ HÒA / phú hòa / Phú Hòa / Phú Hòa", text(filled.getSheetAt(0), 0, 0));
                assertEquals("PHÚ HÒA 1", text(filled.getSheetAt(0), 1, 0));
            }
        }
    }

    @Test
    void keepsPaperSizeAndOrientationOfTheTemplate() throws IOException {
        byte[] result;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("x");
            sheet.createRow(0).createCell(0).setCellValue("{{hoVaTen}}");
            sheet.getPrintSetup().setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
            sheet.getPrintSetup().setLandscape(true);
            workbook.write(out);
            result = RegistrationTemplateRenderer.renderWorkbook(out.toByteArray(), Map.of(), List.of(group("Giáo xứ A", "A", 3)),
                    RegistrationTemplateRenderer.Layout.SINGLE_SHEET, false);
        }
        try (XSSFWorkbook filled = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertEquals(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE, filled.getSheetAt(0).getPrintSetup().getPaperSize());
            assertTrue(filled.getSheetAt(0).getPrintSetup().getLandscape());
        }
    }

    @Test
    void validatesTemplate() throws IOException {
        RegistrationTemplateRenderer.validateTemplate(template());
        assertTrue(RegistrationTemplateRenderer.hasDataRow(template()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("x").createRow(0).createCell(0).setCellValue("Xin chào {{khongCoTruongNay}}");
            workbook.write(out);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> RegistrationTemplateRenderer.validateTemplate(out.toByteArray()));
            assertTrue(error.getMessage().contains("khongCoTruongNay"));
            assertFalse(RegistrationTemplateRenderer.hasDataRow(out.toByteArray()));
        }
    }
}
