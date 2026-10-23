package phucuong.ht.common.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Đọc file .xlsx được tải lên. Cột được nhận theo tên tiêu đề (không phân biệt hoa thường, dấu, dấu *), không phụ thuộc thứ tự cột. */
public final class ExcelWorkbookReader implements AutoCloseable {

    public static final int MAX_DATA_ROWS = 5_000;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("uuuu-M-d")
    };

    private final XSSFWorkbook workbook;

    private ExcelWorkbookReader(XSSFWorkbook workbook) {
        this.workbook = workbook;
    }

    /** Mở file .xlsx đã lưu trên đĩa (tác vụ nền). */
    public static ExcelWorkbookReader open(java.nio.file.Path path) {
        try {
            if (java.nio.file.Files.size(path) > MAX_FILE_BYTES) {
                throw invalid("File Excel vượt quá 20MB");
            }
            try (InputStream in = java.nio.file.Files.newInputStream(path)) {
                return new ExcelWorkbookReader(new XSSFWorkbook(in));
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ApiException api) {
                throw api;
            }
            throw invalid("Không đọc được file Excel. Hãy dùng đúng file mẫu (.xlsx)");
        }
    }

    public static ExcelWorkbookReader open(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Vui lòng chọn file Excel (.xlsx)");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw invalid("Chỉ hỗ trợ file Excel định dạng .xlsx");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw invalid("File Excel vượt quá 20MB");
        }
        try (InputStream in = file.getInputStream()) {
            return new ExcelWorkbookReader(new XSSFWorkbook(in));
        } catch (IOException | RuntimeException exception) {
            throw invalid("Không đọc được file Excel. Hãy dùng đúng file mẫu (.xlsx)");
        }
    }

    /** Lấy sheet theo tên (bỏ qua hoa thường và dấu); trả null nếu không có. */
    public ExcelSheetData sheet(String sheetName) {
        String wanted = normalize(sheetName);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (normalize(workbook.getSheetName(i)).equals(wanted)) {
                return read(workbook.getSheetAt(i));
            }
        }
        return null;
    }

    /** Sheet đầu tiên của file. */
    public ExcelSheetData firstSheet() {
        if (workbook.getNumberOfSheets() == 0) {
            throw invalid("File Excel không có sheet nào");
        }
        return read(workbook.getSheetAt(0));
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException ignored) {
            // không ảnh hưởng kết quả
        }
    }

    private ExcelSheetData read(Sheet sheet) {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> columns = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String header = text(cell);
                if (header != null && !header.isBlank()) {
                    columns.putIfAbsent(normalize(header), cell.getColumnIndex());
                    displayNames.putIfAbsent(normalize(header), header.replace("*", "").trim());
                }
            }
        }
        List<ExcelRowData> rows = new ArrayList<>();
        int first = sheet.getFirstRowNum() + 1;
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            boolean any = false;
            for (Map.Entry<String, Integer> column : columns.entrySet()) {
                Object value = value(row.getCell(column.getValue()));
                if (value != null && !(value instanceof String s && s.isBlank())) {
                    any = true;
                }
                values.put(column.getKey(), value);
            }
            if (!any) {
                continue;
            }
            if (rows.size() >= MAX_DATA_ROWS) {
                throw invalid("Mỗi file chỉ được import tối đa " + MAX_DATA_ROWS + " dòng dữ liệu");
            }
            rows.add(new ExcelRowData(sheet.getSheetName(), r + 1, values));
        }
        return new ExcelSheetData(sheet.getSheetName(), displayNames, rows);
    }

    private static Object value(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate()
                    : (Object) BigDecimal.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> null;
        };
    }

    private static String text(Cell cell) {
        Object value = value(cell);
        return value == null ? null : ExcelRowData.asText(value);
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .replace("*", " ");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static LocalDate parseDate(String text) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text.trim(), format);
            } catch (DateTimeParseException ignored) {
                // thử định dạng kế tiếp
            }
        }
        return null;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }
}
