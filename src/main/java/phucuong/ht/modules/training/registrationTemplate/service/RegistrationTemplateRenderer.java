package phucuong.ht.modules.training.registrationTemplate.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellCopyPolicy;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import phucuong.ht.modules.training.registrationTemplate.model.RegistrationTemplateField;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Điền dữ liệu vào file mẫu danh sách đăng ký (sheet đầu tiên). Mẫu là file Excel do người dùng soạn, trường chèn dạng {{tên}}:
 * - trường kiểu CELL (tên khóa, xứ đoàn, ngày xuất...) được thay ở mọi ô;
 * - dòng đầu tiên có trường kiểu COLUMN (STT, họ và tên...) là dòng mẫu của bảng: được nhân lên theo số người
 *   (chép định dạng, ô gộp, chiều cao; phần bên dưới như chữ ký được đẩy xuống).
 */
public final class RegistrationTemplateRenderer {

    private static final java.util.Locale VI = new java.util.Locale("vi", "VN");
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*(?:\\|\\s*(upper|lower|title)\\s*)?}}");

    /** Một nhóm (giáo hạt / giáo xứ, hoặc cả danh sách): giá trị các trường ô và các dòng dữ liệu (trường cột -> giá trị). */
    public record Group(String name, Map<String, String> values, List<Map<String, Object>> rows) {
    }

    public enum Layout {
        /** Một sheet; nhiều nhóm thì có dòng tên nhóm chen giữa bảng. */
        SINGLE_SHEET,
        /** Mỗi nhóm một sheet. */
        SHEET_PER_GROUP,
        /** Mỗi nhóm một file, gói trong .zip. */
        FILE_PER_GROUP
    }

    private sealed interface Item permits GroupTitle, DataRow {
    }

    private record GroupTitle(String text) implements Item {
    }

    private record DataRow(Map<String, Object> values) implements Item {
    }

    private RegistrationTemplateRenderer() {
    }

    /** Mở thử file mẫu: báo lỗi tiếng Việt nếu không đọc được, có {{trường}} lạ, hoặc không có dòng dữ liệu nào. */
    public static void validateTemplate(byte[] template) {
        try (XSSFWorkbook workbook = open(template)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("File mẫu không có sheet nào");
            }
            XSSFSheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.STRING) {
                        continue;
                    }
                    Matcher matcher = TOKEN.matcher(cell.getStringCellValue());
                    while (matcher.find()) {
                        if (RegistrationTemplateField.byKey(matcher.group(1)).isEmpty()) {
                            throw new IllegalArgumentException("Ô " + cell.getAddress().formatAsString() + " dùng trường {{" + matcher.group(1) + "}} không có trong danh sách trường");
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal && illegal.getMessage() != null
                    && (illegal.getMessage().startsWith("File mẫu") || illegal.getMessage().startsWith("Ô "))) {
                throw illegal;
            }
            throw new IllegalArgumentException("Không đọc được file mẫu. Hãy thử lưu lại mẫu");
        }
    }

    /** Có dòng dữ liệu (chứa trường cột như {{hoVaTen}}) chưa; dùng để báo lỗi sớm khi xuất. */
    public static boolean hasDataRow(byte[] template) {
        try (XSSFWorkbook workbook = open(template)) {
            return dataRowIndex(workbook.getSheetAt(0)) >= 0;
        } catch (IOException exception) {
            return false;
        }
    }

    /** Kết quả .xlsx (SINGLE_SHEET / SHEET_PER_GROUP). groupTitles: chen dòng tên nhóm khi gộp nhiều nhóm vào một sheet. */
    public static byte[] renderWorkbook(byte[] template, Map<String, String> commonValues, List<Group> groups, Layout layout, boolean groupTitles) {
        try (XSSFWorkbook workbook = open(template)) {
            while (workbook.getNumberOfSheets() > 1) {
                workbook.removeSheetAt(workbook.getNumberOfSheets() - 1);
            }
            if (layout == Layout.SHEET_PER_GROUP && !groups.isEmpty()) {
                Set<String> used = new HashSet<>();
                for (Group group : groups) {
                    XSSFSheet sheet = workbook.cloneSheet(0, uniqueSheetName(group.name(), used));
                    fillSheet(sheet, merge(commonValues, group.values()), items(List.of(group), false));
                }
                workbook.removeSheetAt(0);
                workbook.setActiveSheet(0);
                workbook.setSelectedTab(0);
            } else {
                Map<String, String> values = groups.size() == 1 ? merge(commonValues, groups.get(0).values()) : merge(commonValues, totals(groups));
                fillSheet(workbook.getSheetAt(0), values, items(groups, groupTitles && groups.size() > 1));
            }
            return write(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException("Không tạo được file Excel theo mẫu", exception);
        }
    }

    /** Mỗi nhóm một file .xlsx trong một file .zip. */
    public static byte[] renderZip(byte[] template, Map<String, String> commonValues, List<Group> groups, String filePrefix) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            Set<String> used = new HashSet<>();
            for (Group group : groups) {
                byte[] content = renderWorkbook(template, commonValues, List.of(group), Layout.SINGLE_SHEET, false);
                String base = filePrefix + "-" + slug(group.name());
                String name = base;
                for (int i = 2; !used.add(name); i++) {
                    name = base + "-" + i;
                }
                zip.putNextEntry(new ZipEntry(name + ".xlsx"));
                zip.write(content);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không tạo được file nén", exception);
        }
    }

    // ---- điền một sheet ----

    private static void fillSheet(XSSFSheet sheet, Map<String, String> values, List<Item> items) {
        int templateRow = dataRowIndex(sheet);
        if (templateRow < 0) {
            replaceTokens(sheet, values, Map.of(), -1);
            return;
        }
        int needed = Math.max(items.size(), 1);
        int extra = needed - 1;
        if (extra > 0) {
            growTable(sheet, templateRow, extra);
        }
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;
        for (Cell cell : sheet.getRow(templateRow)) {
            if (cell.getCellType() == CellType.STRING && hasColumnToken(cell.getStringCellValue())) {
                minCol = Math.min(minCol, cell.getColumnIndex());
                maxCol = Math.max(maxCol, cell.getColumnIndex());
            }
        }
        // Thay trường ô ở các dòng ngoài bảng trước, rồi mới điền từng dòng bảng.
        replaceTokens(sheet, values, Map.of(), templateRow, templateRow + extra);
        Map<Integer, CellStyle> bold = new HashMap<>();
        for (int i = 0; i < needed; i++) {
            Row row = sheet.getRow(templateRow + i);
            Item item = i < items.size() ? items.get(i) : null;
            if (item instanceof GroupTitle title) {
                writeGroupTitle(sheet, row, minCol, maxCol, title.text(), bold);
            } else {
                Map<String, Object> rowValues = item instanceof DataRow data ? data.values() : Map.of();
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        fillCell(cell, values, rowValues);
                    }
                }
            }
        }
    }

    /** Thêm `extra` dòng ngay dưới dòng mẫu: đẩy phần dưới xuống (kèm ô gộp), chép định dạng + chữ trường + ô gộp của dòng mẫu. */
    private static void growTable(XSSFSheet sheet, int templateRow, int extra) {
        List<CellRangeAddress> below = new ArrayList<>();
        for (int r = sheet.getNumMergedRegions() - 1; r >= 0; r--) {
            CellRangeAddress region = sheet.getMergedRegion(r);
            if (region.getFirstRow() > templateRow) {
                below.add(region);
                sheet.removeMergedRegion(r);
            }
        }
        if (sheet.getLastRowNum() > templateRow) {
            sheet.shiftRows(templateRow + 1, sheet.getLastRowNum(), extra, true, false);
        }
        for (CellRangeAddress region : below) {
            sheet.addMergedRegion(new CellRangeAddress(region.getFirstRow() + extra, region.getLastRow() + extra, region.getFirstColumn(), region.getLastColumn()));
        }
        CellCopyPolicy policy = new CellCopyPolicy.Builder()
                .cellValue(true).cellFormula(false).cellStyle(true).mergedRegions(true).rowHeight(true).build();
        for (int i = 1; i <= extra; i++) {
            sheet.copyRows(templateRow, templateRow, templateRow + i, policy);
        }
    }

    private static void fillCell(Cell cell, Map<String, String> cellValues, Map<String, Object> rowValues) {
        String text = cell.getStringCellValue();
        if (text == null || !text.contains("{{")) {
            return;
        }
        Matcher whole = TOKEN.matcher(text.trim());
        if (whole.matches() && rowValues.get(whole.group(1)) instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        // Vị trí đổi chữ: mỗi {{trường}} (từ, đến trong chữ gốc) được thay bằng đoạn mới dài `length` (dùng dựng lại các đoạn định dạng).
        List<int[]> replaced = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = rowValues.containsKey(key) ? rowValues.get(key) : cellValues.get(key);
            String shown = value == null ? "" : value.toString();
            // {{trường|upper}} / {{trường|lower}}: đổi hoa / thường giá trị của trường.
            if ("upper".equals(matcher.group(2))) {
                shown = shown.toUpperCase(VI);
            } else if ("lower".equals(matcher.group(2))) {
                shown = shown.toLowerCase(VI);
            } else if ("title".equals(matcher.group(2))) {
                shown = titleCase(shown);
            }
            replaced.add(new int[]{matcher.start(), matcher.end(), shown.length()});
            matcher.appendReplacement(out, Matcher.quoteReplacement(shown));
        }
        matcher.appendTail(out);
        if (cell instanceof XSSFCell xssfCell) {
            XSSFRichTextString rich = xssfCell.getRichStringCellValue();
            if (rich != null && rich.numFormattingRuns() > 0) {
                // Ô có định dạng riêng từng đoạn (đậm / nghiêng / gạch chân một phần chữ): giữ định dạng khi thay trường.
                cell.setCellValue(richWithReplacements(rich, out.toString(), replaced));
                return;
            }
        }
        cell.setCellValue(out.toString());
    }

    /** Vị trí mới của một vị trí trong chữ gốc sau khi các {{trường}} được thay bằng giá trị (vị trí nằm trong một trường thì về đầu giá trị thay). */
    private static int mapIndex(int original, List<int[]> replaced) {
        int shift = 0;
        for (int[] item : replaced) {
            if (original >= item[1]) {
                shift += item[2] - (item[1] - item[0]);
            } else if (original > item[0]) {
                return item[0] + shift;
            } else {
                break;
            }
        }
        return original + shift;
    }

    private static XSSFRichTextString richWithReplacements(XSSFRichTextString source, String newText, List<int[]> replaced) {
        XSSFRichTextString result = new XSSFRichTextString(newText);
        for (int i = 0; i < source.numFormattingRuns(); i++) {
            int from = mapIndex(source.getIndexOfFormattingRun(i), replaced);
            int to = mapIndex(source.getIndexOfFormattingRun(i) + source.getLengthOfFormattingRun(i), replaced);
            XSSFFont font = source.getFontOfFormattingRun(i);
            if (font != null && to > from) {
                result.applyFont(from, to, font);
            }
        }
        return result;
    }

    private static void writeGroupTitle(XSSFSheet sheet, Row row, int minCol, int maxCol, String text, Map<Integer, CellStyle> boldStyles) {
        if (maxCol < 0) {
            return;
        }
        for (int r = sheet.getNumMergedRegions() - 1; r >= 0; r--) {
            CellRangeAddress region = sheet.getMergedRegion(r);
            if (region.getFirstRow() <= row.getRowNum() && region.getLastRow() >= row.getRowNum()
                    && region.getLastColumn() >= minCol && region.getFirstColumn() <= maxCol) {
                sheet.removeMergedRegion(r);
            }
        }
        for (int c = minCol; c <= maxCol; c++) {
            Cell cell = row.getCell(c) == null ? row.createCell(c) : row.getCell(c);
            cell.setBlank();
        }
        Cell first = row.getCell(minCol);
        first.setCellValue(text);
        CellStyle base = first.getCellStyle();
        CellStyle bold = boldStyles.computeIfAbsent((int) base.getIndex(), key -> {
            XSSFWorkbook workbook = sheet.getWorkbook();
            CellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(base);
            Font baseFont = workbook.getFontAt(base.getFontIndex());
            Font font = workbook.createFont();
            font.setFontName(baseFont.getFontName());
            font.setFontHeightInPoints(baseFont.getFontHeightInPoints());
            font.setBold(true);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.LEFT);
            return style;
        });
        first.setCellStyle(bold);
        if (maxCol > minCol) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), minCol, maxCol));
        }
    }

    /** Thay trường kiểu CELL ở mọi ô chữ, bỏ qua các dòng từ skipFrom tới skipTo (các dòng của bảng). */
    private static void replaceTokens(XSSFSheet sheet, Map<String, String> values, Map<String, Object> rowValues, int skipFrom) {
        replaceTokens(sheet, values, rowValues, skipFrom, skipFrom);
    }

    private static void replaceTokens(XSSFSheet sheet, Map<String, String> values, Map<String, Object> rowValues, int skipFrom, int skipTo) {
        for (Row row : sheet) {
            if (skipFrom >= 0 && row.getRowNum() >= skipFrom && row.getRowNum() <= skipTo) {
                continue;
            }
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    fillCell(cell, values, rowValues);
                }
            }
        }
    }

    // ---- tiện ích ----

    private static int dataRowIndex(XSSFSheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && hasColumnToken(cell.getStringCellValue())) {
                    return row.getRowNum();
                }
            }
        }
        return -1;
    }

    private static boolean hasColumnToken(String text) {
        if (text == null || !text.contains("{{")) {
            return false;
        }
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            if (RegistrationTemplateField.byKey(matcher.group(1)).map(field -> field.getKind() == RegistrationTemplateField.Kind.COLUMN).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static List<Item> items(List<Group> groups, boolean withTitles) {
        List<Item> items = new ArrayList<>();
        for (Group group : groups) {
            if (withTitles) {
                items.add(new GroupTitle(group.name() + " (" + group.rows().size() + ")"));
            }
            int index = 1;
            for (Map<String, Object> row : group.rows()) {
                Map<String, Object> values = new HashMap<>(row);
                values.put("stt", index++);
                items.add(new DataRow(values));
            }
        }
        return items;
    }

    /** Nhiều nhóm trong một sheet: trường riêng của nhóm để trống, chỉ có tổng số người. */
    private static Map<String, String> totals(List<Group> groups) {
        Map<String, String> values = new HashMap<>();
        Set<String> keys = new HashSet<>();
        groups.forEach(group -> keys.addAll(group.values().keySet()));
        keys.forEach(key -> values.put(key, ""));
        values.put("soNguoi", String.valueOf(groups.stream().mapToInt(group -> group.rows().size()).sum()));
        return values;
    }

    private static Map<String, String> merge(Map<String, String> common, Map<String, String> group) {
        Map<String, String> merged = new HashMap<>(common);
        merged.putAll(group);
        return merged;
    }

    private static XSSFWorkbook open(byte[] template) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(template));
    }

    private static byte[] write(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Viết hoa chữ cái đầu mỗi từ, còn lại viết thường (vd. "PHÚ HÒA" -> "Phú Hòa"). */
    static String titleCase(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean start = true;
        for (char ch : text.toLowerCase(VI).toCharArray()) {
            out.append(start && Character.isLetter(ch) ? Character.toTitleCase(ch) : ch);
            start = Character.isWhitespace(ch) || ch == '(' || ch == '-' || ch == '/';
        }
        return out.toString();
    }

    private static String uniqueSheetName(String name, Set<String> used) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Danh sách";
        }
        cleaned = cleaned.length() > 28 ? cleaned.substring(0, 28).trim() : cleaned;
        String candidate = cleaned;
        for (int i = 2; !used.add(candidate.toLowerCase()); i++) {
            candidate = cleaned + " " + i;
        }
        return candidate;
    }

    static String slug(String text) {
        String plain = java.text.Normalizer.normalize((text == null ? "" : text).replace('đ', 'd').replace('Đ', 'D'), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase();
        if (plain.isEmpty()) {
            return "danh-sach";
        }
        return plain.length() > 60 ? plain.substring(0, 60).replaceAll("-+$", "") : plain;
    }
}
