package phucuong.ht.common.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.exception.ApiException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelRoundTripTest {

    private record Person(String name, LocalDate birth, BigDecimal score, String phone) {
    }

    private static MockMultipartFile asUpload(byte[] content, String fileName) {
        return new MockMultipartFile("file", fileName, ExcelResponses.XLSX.toString(), content);
    }

    @Test
    void writerOutputCanBeReadBackByColumnNameRegardlessOfOrderAndDiacritics() {
        List<ExcelColumn<Person>> columns = List.of(
                ExcelColumn.of("Họ và tên *", Person::name),
                ExcelColumn.of("Ngày sinh", Person::birth),
                ExcelColumn.of("Tổng điểm", Person::score),
                ExcelColumn.of("Số điện thoại", Person::phone));
        byte[] content = ExcelExportWriter.write("Mẫu", columns, List.of(
                new Person("Nguyễn Văn An", LocalDate.of(1995, 3, 7), new BigDecimal("8.5"), "0901234567"),
                new Person("Trần Thị Bình", null, null, null)));

        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(asUpload(content, "mau.xlsx"))) {
            ExcelSheetData sheet = reader.firstSheet();
            assertEquals(2, sheet.rows().size());
            // Tên cột không phân biệt hoa thường, dấu và dấu * bắt buộc.
            assertTrue(sheet.hasColumn("ho va ten"));
            assertTrue(sheet.hasColumn("SỐ ĐIỆN THOẠI"));

            ImportErrors errors = new ImportErrors();
            ExcelRowData first = sheet.rows().get(0);
            assertEquals("Nguyễn Văn An", first.requiredText(errors, "Họ và tên"));
            assertEquals(LocalDate.of(1995, 3, 7), first.date(errors, "Ngày sinh", true));
            assertEquals(0, new BigDecimal("8.5").compareTo(first.decimal(errors, "Tổng điểm", true)));
            assertEquals(2, first.number());
            assertTrue(errors.isEmpty());

            ExcelRowData second = sheet.rows().get(1);
            assertNull(second.date(errors, "Ngày sinh", false));
            assertTrue(errors.isEmpty());
            assertNull(second.date(errors, "Ngày sinh", true));
            assertEquals(1, errors.count());
        }
    }

    @Test
    void phoneNumbersLoseLeadingZeroInNumericCellsButAreRestored() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("Dữ liệu");
        sheet.createRow(0).createCell(0).setCellValue("Số điện thoại");
        sheet.createRow(1).createCell(0).setCellValue(901234567d);
        sheet.createRow(2).createCell(0).setCellValue("+84 90 123 4568");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(asUpload(out.toByteArray(), "sdt.xlsx"))) {
            ExcelSheetData data = reader.firstSheet();
            assertEquals("0901234567", data.rows().get(0).phone("Số điện thoại"));
            assertEquals("0901234568", data.rows().get(1).phone("Số điện thoại"));
        }
    }

    @Test
    void invalidDateAndNumberAreReportedWithSheetRowAndColumn() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("Học viên");
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("Ngày sinh");
        header.createCell(1).setCellValue("Tổng điểm");
        var row = sheet.createRow(1);
        row.createCell(0).setCellValue("không phải ngày");
        row.createCell(1).setCellValue("abc");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(asUpload(out.toByteArray(), "loi.xlsx"))) {
            ExcelRowData data = reader.firstSheet().rows().get(0);
            ImportErrors errors = new ImportErrors();
            data.date(errors, "Ngày sinh", true);
            data.decimal(errors, "Tổng điểm", true);
            ImportValidationException exception = assertThrows(ImportValidationException.class, errors::throwIfAny);
            assertEquals(2, exception.getErrors().size());
            ImportError first = exception.getErrors().get(0);
            assertEquals("Học viên", first.sheet());
            assertEquals(2, first.row());
            assertEquals("Ngày sinh", first.column());
        }
    }

    @Test
    void missingRequiredColumnsAreReported() {
        byte[] content = ExcelExportWriter.write("Sheet", List.of(ExcelColumn.<Person>of("Họ và tên", Person::name)), List.of());
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(asUpload(content, "thieu.xlsx"))) {
            ImportErrors errors = new ImportErrors();
            assertFalse(reader.firstSheet().requireColumns(errors, "Họ và tên", "Email"));
            assertEquals(1, errors.count());
        }
    }

    @Test
    void rejectsNonXlsxAndEmptyUploads() {
        assertThrows(ApiException.class, () -> ExcelWorkbookReader.open(asUpload(new byte[]{1, 2, 3}, "mau.csv")));
        assertThrows(ApiException.class, () -> ExcelWorkbookReader.open(asUpload(new byte[0], "mau.xlsx")));
        assertThrows(ApiException.class, () -> ExcelWorkbookReader.open(asUpload(new byte[]{1, 2, 3}, "hong.xlsx")));
    }

    @Test
    void importErrorsKeepOnlyTheFirstBatchButCountEverything() {
        ImportErrors errors = new ImportErrors();
        for (int i = 0; i < ImportErrors.LIMIT + 5; i++) {
            errors.add("S", i + 2, "C", "lỗi");
        }
        ImportValidationException exception = assertThrows(ImportValidationException.class, errors::throwIfAny);
        // LIMIT lỗi đầu tiên và một dòng thông báo "còn N lỗi khác".
        assertEquals(ImportErrors.LIMIT + 1, exception.getErrors().size());
        assertTrue(exception.getErrors().get(ImportErrors.LIMIT).message().contains("5"));
    }

    @Test
    void exportSupportCollectsEveryPageWithTheSameFilters() {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSearch("an");
        request.getFilters().put("status", true);
        List<BaseSearchRequest> seen = new ArrayList<>();
        List<Integer> all = ExcelExportSupport.collectAll(request, page -> {
            seen.add(page);
            int from = page.getPage() * page.getSize();
            List<Integer> content = new ArrayList<>();
            for (int i = from; i < Math.min(from + page.getSize(), 250); i++) {
                content.add(i);
            }
            return new PageImpl<>(content, PageRequest.of(page.getPage(), page.getSize()), 250);
        });
        assertEquals(250, all.size());
        assertEquals(3, seen.size());
        assertEquals("an", seen.get(2).getSearch());
        assertEquals(Boolean.TRUE, seen.get(2).getFilters().get("status"));
        assertNotNull(seen.get(0).getSortDirection());
    }

    @Test
    void exportSupportRefusesHugeResults() {
        assertThrows(ApiException.class, () -> ExcelExportSupport.collectAll(new BaseSearchRequest(),
                page -> new PageImpl<>(List.of(1), PageRequest.of(0, 100), ExcelExportSupport.MAX_EXPORT_ROWS + 1L)));
    }

    @Test
    void responseFileNameIsAsciiWithDate() {
        var response = ExcelResponses.xlsx(new byte[]{1}, "Giáo phận Đà Lạt");
        String disposition = response.getHeaders().getContentDisposition().getFilename();
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("Giao-phan-Da-Lat-"));
        assertTrue(disposition.endsWith(".xlsx"));
        assertEquals(ExcelResponses.XLSX, response.getHeaders().getContentType());
    }

    @Test
    void readerLeavesBlankRowsOut() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("A");
        sheet.createRow(0).createCell(0).setCellValue("Email");
        sheet.createRow(1).createCell(0).setCellValue("a@x.vn");
        sheet.createRow(2).createCell(0).setCellValue("   ");
        sheet.createRow(4).createCell(0).setCellValue("b@x.vn");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(asUpload(out.toByteArray(), "a.xlsx"))) {
            ExcelSheetData data = reader.firstSheet();
            assertEquals(2, data.rows().size());
            assertEquals(5, data.rows().get(1).number());
        }
        assertEquals(0, new ByteArrayInputStream(new byte[0]).available());
    }
}
