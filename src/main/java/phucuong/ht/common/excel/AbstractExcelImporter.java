package phucuong.ht.common.excel;

import phucuong.ht.common.excel.job.JobProgress;

import java.nio.file.Path;
import java.util.List;

/**
 * Khung chung của mọi lần nhập Excel (Template Method): đọc và kiểm tra file, báo lỗi theo dòng, ghi từng dòng
 * trong transaction của người gọi, báo tiến độ rồi tổng kết. Lớp con chỉ khai báo từng bước riêng.
 * <p>
 * Importer là bean dùng chung nhiều luồng nên mọi trạng thái của một lần chạy (người thực hiện, bộ đếm, kế hoạch gắn ảnh...)
 * phải nằm trong đối tượng ngữ cảnh {@code C}, không nằm trong field của importer.
 *
 * @param <R> một dòng dữ liệu đã đọc và kiểm tra hợp lệ
 * @param <C> ngữ cảnh của một lần nhập
 */
public abstract class AbstractExcelImporter<R, C> {

    /** Cứ sau chừng này dòng được ghi thì báo tiến độ một lần. */
    protected static final int PROGRESS_STEP = 200;

    /** Kiểm tra cột bắt buộc, đọc và kiểm tra từng dòng; lỗi cộng dồn vào {@code errors} và được ném sau bước này. */
    protected abstract List<R> readRows(ExcelWorkbookReader reader, C context, ImportErrors errors);

    /** Chạy ngay trước khi ghi dòng đầu tiên (vd. chốt thời điểm ghi). */
    protected void beforeWrite(C context) {
    }

    protected abstract void write(R row, C context);

    /** Chạy sau khi ghi xong mọi dòng (vd. kích hoạt gửi email). */
    protected void afterWrite(C context) {
    }

    protected abstract ImportSummary summarize(int rowCount, C context);

    protected final ImportSummary runImport(Path file, JobProgress progress, C context) {
        List<R> rows;
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(file)) {
            ImportErrors errors = new ImportErrors();
            rows = readRows(reader, context, errors);
            errors.throwIfAny();
        }
        progress.report(0, rows.size());

        beforeWrite(context);
        int written = 0;
        for (R row : rows) {
            write(row, context);
            written++;
            if (written % PROGRESS_STEP == 0) {
                progress.report(written, rows.size());
            }
        }
        afterWrite(context);
        return summarize(rows.size(), context);
    }
}
