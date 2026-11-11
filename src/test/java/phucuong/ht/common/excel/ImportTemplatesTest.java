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
            assertTrue(sheet.hasColumn(LeaderImportService.COL_AVATAR_FILE));
            // Dòng ví dụ đọc được: ngày sinh là ngày, số điện thoại giữ số 0 đầu.
            ExcelRowData first = sheet.rows().get(0);
            ImportErrors rowErrors = new ImportErrors();
            assertEquals("0901234567", first.phone(LeaderImportService.COL_PHONE));
            assertEquals(1995, first.date(rowErrors, LeaderImportService.COL_BIRTH_DATE, true).getYear());
            assertTrue(rowErrors.isEmpty());
        }
    }

    @Test
    void teamTemplateHasTheTeamColumnAndNoExceptionSheet() {
        byte[] content = ImportTemplates.teamParticipants(List.of("Dự trưởng"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertEquals(ImportTemplates.PARTICIPANTS_SHEET, sheet.name());
            assertTrue(sheet.requireColumns(new ImportErrors(), PastCourseParticipantImportService.COL_TEAM,
                    PastCourseParticipantImportService.COL_EMAIL, PastCourseParticipantImportService.COL_PHONE));
            assertEquals(null, reader.sheet(ImportTemplates.EXCEPTION_SHEET));
            assertEquals("Đội 1", sheet.rows().get(0).text(PastCourseParticipantImportService.COL_TEAM));
        }
    }

    @Test
    void regularParticipantTemplateAcceptsAvatarFileNamesOnBothSheets() {
        byte[] content = ImportTemplates.regularParticipants(List.of("Dự trưởng"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData sheet = reader.firstSheet();
            ExcelSheetData exceptions = reader.sheet(ImportTemplates.EXCEPTION_SHEET);
            assertEquals(ImportTemplates.PARTICIPANTS_SHEET, sheet.name());
            // Cột ảnh là tùy chọn nhưng có mặt ở sheet chính và sheet Ngoại lệ, dòng ví dụ ghi sẵn tên file mẫu.
            assertTrue(sheet.hasColumn(LeaderImportService.COL_AVATAR_FILE));
            assertEquals("nguyen-van-an.jpg", sheet.rows().get(0).text(LeaderImportService.COL_AVATAR_FILE));
            assertEquals(null, sheet.rows().get(1).text(LeaderImportService.COL_AVATAR_FILE));
            assertTrue(exceptions.hasColumn(LeaderImportService.COL_AVATAR_FILE));
            // Thêm cột ảnh không làm lệch các cột sau nó: lí do ngoại lệ vẫn đọc đúng.
            assertEquals("Chưa đủ số năm hoạt động nhưng được giáo xứ đề cử",
                    exceptions.rows().get(0).text(PastCourseParticipantImportService.COL_EXCEPTION_REASON));
            assertEquals("Ghi chú tùy chọn", sheet.rows().get(0).text(PastCourseParticipantImportService.COL_NOTE));
        }
    }

    @Test
    void teamTemplateAcceptsAvatarFileNamesAndKeepsTheTeamColumn() {
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(ImportTemplates.teamParticipants(List.of("Dự trưởng"))))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertTrue(sheet.hasColumn(LeaderImportService.COL_AVATAR_FILE));
            assertEquals("nguyen-van-an.jpg", sheet.rows().get(0).text(LeaderImportService.COL_AVATAR_FILE));
            // Thêm cột ảnh không làm lệch cột Tên đội và Ghi chú.
            assertEquals("Đội 1", sheet.rows().get(0).text(PastCourseParticipantImportService.COL_TEAM));
            assertEquals("Ghi chú tùy chọn", sheet.rows().get(0).text(PastCourseParticipantImportService.COL_NOTE));
            assertEquals(null, reader.sheet(ImportTemplates.EXCEPTION_SHEET));
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
            assertTrue(sheet.hasColumn(LeaderImportService.COL_AVATAR_FILE));
            assertEquals(2, sheet.rows().size());
            // Dòng ví dụ thứ hai là huynh trưởng chưa có hồ sơ, có đủ thông tin tạo mới.
            assertEquals("Trần Thị Bình", sheet.rows().get(1).text(LeaderImportService.COL_FULL_NAME));
            assertEquals("0912345678", sheet.rows().get(1).phone(PastCourseParticipantImportService.COL_PHONE));
        }
    }

    @Test
    void pastParticipantTemplateHasAnOptionalAchievedLevelColumnWithTheSameDropdownAsLevel() throws Exception {
        byte[] content = ImportTemplates.participants(List.of("Dự trưởng", "Huynh trưởng cấp I"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertTrue(sheet.hasColumn(PastCourseParticipantImportService.COL_ACHIEVED_LEVEL));
            // Cột mới là tùy chọn và không làm lệch các cột khác (cấp độ hiện tại, nhận xét, họ tên vẫn đọc đúng).
            assertTrue(sheet.requireColumns(new ImportErrors(), PastCourseParticipantImportService.COL_RESULT, PastCourseParticipantImportService.COL_SCORE));
            assertEquals("Hoàn thành tốt khóa huấn luyện", sheet.rows().get(0).text(PastCourseParticipantImportService.COL_REVIEW));
            assertEquals("Nguyễn Văn An", sheet.rows().get(0).text(LeaderImportService.COL_FULL_NAME));
            assertEquals(null, sheet.rows().get(0).text(PastCourseParticipantImportService.COL_ACHIEVED_LEVEL));
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            org.apache.poi.ss.usermodel.Row header = workbook.getSheetAt(0).getRow(0);
            int achievedColumn = -1;
            int levelColumn = -1;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String title = header.getCell(c).getStringCellValue();
                if (title.equals(PastCourseParticipantImportService.COL_ACHIEVED_LEVEL)) {
                    achievedColumn = c;
                } else if (title.equals(LeaderImportService.COL_LEVEL)) {
                    levelColumn = c;
                }
            }
            assertTrue(achievedColumn >= 0 && levelColumn >= 0);
            final int achieved = achievedColumn;
            final int level = levelColumn;
            // Hai cột đều dùng danh sách chọn lấy từ sheet "Danh mục".
            for (int column : new int[]{achieved, level}) {
                assertTrue(workbook.getSheetAt(0).getDataValidations().stream().anyMatch(validation ->
                        validation.getRegions().getCellRangeAddress(0).getFirstColumn() == column
                                && validation.getValidationConstraint().getFormula1().contains("Danh mục")), "cột " + column);
            }
        }
    }

    @Test
    void regularTemplateHasNoAchievedLevelColumnAndWarnsAboutExampleRows() throws Exception {
        byte[] content = ImportTemplates.regularParticipants(List.of("Dự trưởng", "Huynh trưởng cấp I"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData main = reader.firstSheet();
            ExcelSheetData exceptions = reader.sheet(ImportTemplates.EXCEPTION_SHEET);
            // Cấp độ đạt được chỉ có ở mẫu khóa quá khứ; sheet Ngoại lệ vẫn chờ duyệt đăng ký nên người duyệt chọn cấp.
            assertFalse(main.hasColumn(PastCourseParticipantImportService.COL_ACHIEVED_LEVEL));
            assertFalse(exceptions.hasColumn(PastCourseParticipantImportService.COL_ACHIEVED_LEVEL));
            // Lí do ngoại lệ vẫn bắt buộc và đọc đúng sau khi thêm cột.
            assertTrue(exceptions.requireColumns(new ImportErrors(), PastCourseParticipantImportService.COL_EXCEPTION_REASON));
            assertEquals("Chưa đủ số năm hoạt động nhưng được giáo xứ đề cử",
                    exceptions.rows().get(0).text(PastCourseParticipantImportService.COL_EXCEPTION_REASON));
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            org.apache.poi.ss.usermodel.Sheet guide = workbook.getSheet("Hướng dẫn");
            StringBuilder text = new StringBuilder();
            for (int r = 0; r <= guide.getLastRowNum(); r++) {
                if (guide.getRow(r) != null && guide.getRow(r).getCell(0) != null) {
                    text.append(guide.getRow(r).getCell(0).getStringCellValue()).append('\n');
                }
            }
            // Cảnh báo xóa dòng ví dụ; sheet Ngoại lệ vẫn được mô tả là chờ duyệt đăng ký.
            assertTrue(text.toString().contains("LƯU Ý QUAN TRỌNG"), text.toString());
            assertTrue(text.toString().contains("XÓA HẾT dòng ví dụ"), text.toString());
            assertTrue(text.toString().contains("CHỜ DUYỆT đăng ký"), text.toString());
            assertFalse(text.toString().contains("Cấp độ đạt được"), text.toString());
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
            // Kết quả, Giới tính, Cấp độ và Cấp độ đạt được có danh sách chọn.
            assertEquals(4, validations.size());
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

    @Test
    void deaneryTemplateOnlyRequiresTheNameColumn() {
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(ImportTemplates.deaneries()))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertEquals(ImportTemplates.DEANERIES_SHEET, sheet.name());
            assertTrue(sheet.requireColumns(new ImportErrors(), phucuong.ht.modules.deanery.service.DeaneryImportService.COL_NAME));
            assertEquals("0901234567", sheet.rows().get(0).phone(phucuong.ht.modules.deanery.service.DeaneryImportService.COL_PHONE));
            assertEquals(null, sheet.rows().get(1).text(phucuong.ht.modules.deanery.service.DeaneryImportService.COL_EMAIL));
        }
    }

    @Test
    void parishTemplateHasTypeAndDeaneryDropdowns() throws Exception {
        byte[] content = ImportTemplates.parishes(List.of("Giáo hạt A", "Giáo hạt B"));
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(upload(content))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertEquals(ImportTemplates.PARISHES_SHEET, sheet.name());
            assertTrue(sheet.requireColumns(new ImportErrors(), phucuong.ht.modules.parish.service.ParishImportService.COL_NAME, phucuong.ht.modules.parish.service.ParishImportService.COL_DEANERY));
            assertTrue(sheet.hasColumn(phucuong.ht.modules.parish.service.ParishImportService.COL_TYPE));
            assertTrue(sheet.hasColumn(phucuong.ht.modules.parish.service.ParishImportService.COL_PARENT));
            assertEquals("Giáo hạt A", sheet.rows().get(0).text(phucuong.ht.modules.parish.service.ParishImportService.COL_DEANERY));
        }
        String xml = sheetXml(content);
        assertTrue(xml.contains("'Danh mục'!$A$2:$A$3"), xml);
        assertTrue(xml.contains("Giáo họ,Giáo điểm"), xml);
    }
}
