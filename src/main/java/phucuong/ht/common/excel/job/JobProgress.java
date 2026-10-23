package phucuong.ht.common.excel.job;

/** Báo tiến độ cho tác vụ nền. Với xuất, report còn là điểm kiểm tra người dùng đã yêu cầu hủy chưa (ném {@link JobCancelledException}). */
@FunctionalInterface
public interface JobProgress {

    JobProgress NONE = (processed, total) -> {
    };

    void report(int processed, int total);

    class JobCancelledException extends RuntimeException {
        public JobCancelledException() {
            super("Tác vụ đã bị hủy");
        }
    }
}
