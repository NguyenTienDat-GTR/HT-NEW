package phucuong.ht.common.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractExcelImporterTest {

    @TempDir
    Path dir;

    private Path emptyWorkbook() throws IOException {
        Path file = dir.resolve("in.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            workbook.createSheet("Sheet1");
            workbook.write(out);
        }
        return file;
    }

    /** Importer thử nghiệm: ghi lại thứ tự các bước để kiểm tra khung chạy đúng. */
    private static final class Recorder extends AbstractExcelImporter<Integer, List<String>> {
        private final int rows;
        private final boolean failOnRead;

        Recorder(int rows, boolean failOnRead) {
            this.rows = rows;
            this.failOnRead = failOnRead;
        }

        @Override
        protected List<Integer> readRows(ExcelWorkbookReader reader, List<String> log, ImportErrors errors) {
            log.add("read");
            if (failOnRead) {
                errors.add("Sheet1", 2, "Email", "sai định dạng");
            }
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                result.add(i);
            }
            return result;
        }

        @Override
        protected void beforeWrite(List<String> log) {
            log.add("before");
        }

        @Override
        protected void write(Integer row, List<String> log) {
            log.add("write");
        }

        @Override
        protected void afterWrite(List<String> log) {
            log.add("after");
        }

        @Override
        protected ImportSummary summarize(int rowCount, List<String> log) {
            return new ImportSummary(rowCount, "xong " + rowCount);
        }

        ImportSummary run(Path file, List<int[]> progress, List<String> log) {
            return runImport(file, (processed, total) -> progress.add(new int[]{processed, total}), log);
        }
    }

    @Test
    void runsStepsInOrderAndReportsProgressEveryStep() throws IOException {
        List<String> log = new ArrayList<>();
        List<int[]> progress = new ArrayList<>();

        ImportSummary summary = new Recorder(450, false).run(emptyWorkbook(), progress, log);

        assertEquals(450, summary.created());
        assertEquals("xong 450", summary.message());
        assertEquals("read", log.get(0));
        assertEquals("before", log.get(1));
        assertEquals("after", log.get(log.size() - 1));
        assertEquals(450, log.stream().filter("write"::equals).count());
        // báo 0 lúc bắt đầu, sau đó mỗi 200 dòng
        assertEquals(List.of(List.of(0, 450), List.of(200, 450), List.of(400, 450)),
                progress.stream().map(p -> List.of(p[0], p[1])).toList());
    }

    @Test
    void stopsBeforeWritingWhenRowsHaveErrors() throws IOException {
        List<String> log = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> new Recorder(3, true).run(emptyWorkbook(), new ArrayList<>(), log));

        assertEquals(List.of("read"), log);
    }
}
