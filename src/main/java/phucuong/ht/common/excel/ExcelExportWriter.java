package phucuong.ht.common.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Ghi danh sách dữ liệu ra file .xlsx (phần đầu đơn vị + tiêu đề, dòng tiêu đề cột đậm, cố định tới dòng tiêu đề cột, có bộ lọc).
 * {@link #writeNumbered} thêm cột đầu tiên là số thứ tự (STT), không nằm trong danh sách cột người dùng chọn; {@link #write} thì không
 * (dùng khi dựng file nhập / file mà thứ tự cột phải đúng như danh sách cột).
 */
public final class ExcelExportWriter {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ORDER_HEADER = "STT";

    private ExcelExportWriter() {
    }

    public static <T> byte[] write(String sheetName, List<ExcelColumn<T>> columns, List<T> rows) {
        return write(sheetName, (ExcelReportHeader) null, columns, rows);
    }

    /**
     * header != null: các dòng đơn vị rồi dòng tiêu đề (gộp ô theo số cột, căn giữa, in đậm), một dòng trống,
     * sau đó mới tới dòng tiêu đề cột.
     */
    public static <T> byte[] write(String sheetName, ExcelReportHeader header, List<ExcelColumn<T>> columns, List<T> rows) {
        return write(sheetName, header, columns, rows, false);
    }

    /** Như {@link #write(String, ExcelReportHeader, List, List)} nhưng thêm cột STT (1, 2, 3, …) ở đầu mỗi dòng. */
    public static <T> byte[] writeNumbered(String sheetName, ExcelReportHeader header, List<ExcelColumn<T>> columns, List<T> rows) {
        return write(sheetName, header, columns, rows, true);
    }

    private static <T> byte[] write(String sheetName, ExcelReportHeader header, List<ExcelColumn<T>> columns, List<T> rows, boolean numbered) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy"));
            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy HH:mm"));

            // Có cột STT đứng đầu thì mọi cột dữ liệu lùi một ô.
            int shift = numbered ? 1 : 0;
            int totalColumns = columns.size() + shift;
            int headerIndex = 0;
            if (header != null && !header.isEmpty() && !columns.isEmpty()) {
                CellStyle unitStyle = centered(workbook, 12);
                for (String line : header.unitLines()) {
                    mergedLine(sheet, headerIndex++, line, unitStyle, totalColumns);
                }
                if (header.title() != null && !header.title().isBlank()) {
                    if (headerIndex > 0) {
                        headerIndex++;
                    }
                    mergedLine(sheet, headerIndex++, header.title(), centered(workbook, 14), totalColumns);
                }
                headerIndex++;
            }
            Row headerRow = sheet.createRow(headerIndex);
            if (numbered) {
                Cell orderHeader = headerRow.createCell(0);
                orderHeader.setCellValue(ORDER_HEADER);
                orderHeader.setCellStyle(headerStyle);
                sheet.setColumnWidth(0, 7 * 256);
            }
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = headerRow.createCell(c + shift);
                cell.setCellValue(columns.get(c).header());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c + shift, Math.min(Math.max(columns.get(c).width(), 8), 80) * 256);
            }
            sheet.createFreezePane(0, headerIndex + 1);

            int rowIndex = headerIndex + 1;
            int order = 1;
            for (T item : rows) {
                Row row = sheet.createRow(rowIndex++);
                if (numbered) {
                    row.createCell(0).setCellValue(order++);
                }
                for (int c = 0; c < columns.size(); c++) {
                    writeCell(row.createCell(c + shift), columns.get(c).value().apply(item), dateStyle, dateTimeStyle);
                }
            }
            if (!columns.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(headerIndex, headerIndex, 0, totalColumns - 1));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không thể tạo file Excel", exception);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // không ảnh hưởng kết quả
            }
        }
    }

    private static CellStyle centered(SXSSFWorkbook workbook, int size) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) size);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static void mergedLine(Sheet sheet, int rowIndex, String text, CellStyle style, int columnCount) {
        Cell cell = sheet.createRow(rowIndex).createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        if (columnCount > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, columnCount - 1));
        }
    }

    private static void writeCell(Cell cell, Object value, CellStyle dateStyle, CellStyle dateTimeStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof MonthDay monthDay) {
            cell.setCellValue(String.format("%02d/%02d", monthDay.getDayOfMonth(), monthDay.getMonthValue()));
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof OffsetDateTime time) {
            cell.setCellValue(time.atZoneSameInstant(ZONE).toLocalDateTime());
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof LocalDateTime time) {
            cell.setCellValue(time);
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof Boolean flag) {
            cell.setCellValue(flag ? "Có" : "Không");
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            String text = value.toString();
            cell.setCellValue(text.length() > 32000 ? text.substring(0, 32000) : text);
        }
    }

    private static String safeSheetName(String name) {
        String cleaned = name == null ? "Dữ liệu" : name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Dữ liệu";
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
