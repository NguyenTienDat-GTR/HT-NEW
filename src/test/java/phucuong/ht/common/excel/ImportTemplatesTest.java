package phucuong.ht.common.excel;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.training.trainingCourse.service.PastCourseParticipantImportService;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportTemplatesTest {

    private static MockMultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "mau.xlsx", ExcelResponses.XLSX.toString(), content);
    }

    @Test
    void leaderTemplateIsReadableByTheImportReaderWithEveryColumnTheImportNeeds() {
        byte[] content = ImportTemplates.leaders(List.of("Dự trưởng", "Huynh trưởng cấp I"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertEquals(ImportTemplates.LEADERS_SHEET, sheet.name());
            ImportErrors errors = new ImportErrors();
            // Đúng các cột bắt buộc mà import hồ sơ huynh trưởng yêu cầu.
            assertTrue(sheet.requireColumns(errors, LeaderImportService.COL_HOLY_NAME, LeaderImportService.COL_FULL_NAME,
                    LeaderImportService.COL_BIRTH_DATE, LeaderImportService.COL_GENDER, LeaderImportService.COL_EMAIL, LeaderImportService.COL_PARISH));
            assertTrue(sheet.hasColumn(LeaderImportService.COL_PHONE));
            assertTrue(sheet.hasColumn(LeaderImportService.COL_LEVEL));
            assertTrue(sheet.hasColumn(LeaderImportService.COL_DEANERY));
            // Dòng ví dụ đọc được: ngày sinh là ngày, số điện thoại giữ số 0 đầu.
            ExcelRowData first = sheet.rows().get(0);
            ImportErrors rowErrors = new ImportErrors();
            assertEquals("0901234567", first.phone(LeaderImportService.COL_PHONE));
            assertEquals(1995, first.date(rowErrors, LeaderImportService.COL_BIRTH_DATE, true).getYear());
            assertTrue(rowErrors.isEmpty());
        }
    }

    @Test
    void participantTemplateHasTheResultColumnAndTheOptionalLeaderColumns() {
        byte[] content = ImportTemplates.participants(List.of("Dự trưởng"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertEquals(ImportTemplates.PARTICIPANTS_SHEET, sheet.name());
            assertTrue(sheet.requireColumns(new ImportErrors(), PastCourseParticipantImportService.COL_RESULT));
            for (String column : List.of(PastCourseParticipantImportService.COL_EMAIL, PastCourseParticipantImportService.COL_PHONE,
                    PastCourseParticipantImportService.COL_SCORE, PastCourseParticipantImportService.COL_REVIEW,
                    LeaderImportService.COL_HOLY_NAME, LeaderImportService.COL_FULL_NAME, LeaderImportService.COL_BIRTH_DATE,
                    LeaderImportService.COL_GENDER, LeaderImportService.COL_LEVEL, LeaderImportService.COL_DEANERY, LeaderImportService.COL_PARISH)) {
                assertTrue(sheet.hasColumn(column), column);
            }
            assertEquals(2, sheet.rows().size());
            // Dòng ví dụ thứ hai là huynh trưởng chưa có hồ sơ, có đủ thông tin tạo mới.
            assertEquals("Trần Thị Bình", sheet.rows().get(1).text(LeaderImportService.COL_FULL_NAME));
            assertEquals("0912345678", sheet.rows().get(1).phone(PastCourseParticipantImportService.COL_PHONE));
        }
    }

    @Test
    void templatesHaveGuideAndLevelLookupSheetsAfterTheDataSheetAndDropdownsForChoices() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(ImportTemplates.participants(List.of("Dự trưởng", "Huynh trưởng cấp I"))))) {
            assertEquals(ImportTemplates.PARTICIPANTS_SHEET, workbook.getSheetName(0));
            assertEquals("Danh mục", workbook.getSheetName(1));
            assertEquals("Hướng dẫn", workbook.getSheetName(2));
            assertEquals("Huynh trưởng cấp I", workbook.getSheet("Danh mục").getRow(2).getCell(0).getStringCellValue());
            List<? extends DataValidation> validations = workbook.getSheetAt(0).getDataValidations();
            // Kết quả, Giới tính và Cấp độ có danh sách chọn.
            assertEquals(3, validations.size());
            assertFalse(validations.stream().anyMatch(validation -> validation.getShowErrorBox()), "vẫn cho nhập tay giá trị khác");
        }
    }

    // Trong XML của Excel, showDropDown="1" nghĩa là ẨN mũi tên danh sách chọn: mẫu phải hiện mũi tên để người dùng chọn cấp độ / giới tính / kết quả.
    private static String sheetXml(byte[] content) throws Exception {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(content))) {
            for (java.util.zip.ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("không có sheet1.xml");
    }

    @Test
    void dropdownArrowsAreVisibleAndTheLevelListPointsAtTheLookupSheet() throws Exception {
        for (byte[] content : List.of(ImportTemplates.leaders(List.of("Dự trưởng", "Huynh trưởng cấp I")),
                ImportTemplates.participants(List.of("Dự trưởng", "Huynh trưởng cấp I")))) {
            String xml = sheetXml(content);
            assertTrue(xml.contains("<dataValidation"), xml);
            assertFalse(xml.contains("showDropDown=\"1\"") || xml.contains("showDropDown=\"true\""), "mũi tên danh sách chọn bị ẩn");
            assertTrue(xml.contains("'Danh mục'!$A$2:$A$3"), xml);
        }
    }

    @Test
    void templateWithoutLevelsStillWorks() {
        byte[] content = ImportTemplates.leaders(List.of());
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            assertEquals(2, reader.firstSheet().rows().size());
        }
    }
}
