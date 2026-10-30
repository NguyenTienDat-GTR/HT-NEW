package phucuong.ht.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.errorCode;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final errorCode errorCode;

    public ApiException(HttpStatus status, errorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
