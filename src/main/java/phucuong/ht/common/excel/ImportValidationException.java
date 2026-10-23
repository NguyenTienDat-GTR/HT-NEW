package phucuong.ht.common.excel;

import lombok.Getter;

import java.util.List;

@Getter
public class ImportValidationException extends RuntimeException {

    private final transient List<ImportError> errors;

    public ImportValidationException(List<ImportError> errors) {
        super("File Excel có lỗi, chưa có dữ liệu nào được lưu. Vui lòng sửa file rồi import lại");
        this.errors = List.copyOf(errors);
    }
}
