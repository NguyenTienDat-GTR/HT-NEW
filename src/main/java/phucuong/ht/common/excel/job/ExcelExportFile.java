package phucuong.ht.common.excel.job;

/** Kết quả xuất: nội dung file và phần mở rộng (xlsx, hoặc zip khi xuất mỗi nhóm một file). */
public record ExcelExportFile(byte[] content, String extension) {

    public static ExcelExportFile xlsx(byte[] content) {
        return new ExcelExportFile(content, "xlsx");
    }

    public static ExcelExportFile zip(byte[] content) {
        return new ExcelExportFile(content, "zip");
    }
}
