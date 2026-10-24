package phucuong.ht.common.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class globalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<apiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(apiResponse.error(exception.getMessage(), exception.getErrorCode().name()));
    }

    @ExceptionHandler(phucuong.ht.common.excel.ImportValidationException.class)
    public ResponseEntity<apiResponse<java.util.List<phucuong.ht.common.excel.ImportError>>> handleImportValidation(
            phucuong.ht.common.excel.ImportValidationException exception
    ) {
        return ResponseEntity.badRequest()
                .body(apiResponse.<java.util.List<phucuong.ht.common.excel.ImportError>>builder()
                        .success(false)
                        .message(exception.getMessage())
                        .errorCode(errorCode.VALIDATION_ERROR.name())
                        .data(exception.getErrors())
                        .timestamp(java.time.OffsetDateTime.now())
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<apiResponse<Void>> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(apiResponse.error("Tên đăng nhập hoặc mật khẩu không đúng", errorCode.AUTHENTICATION_FAILED.name()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<apiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(apiResponse.error("Bạn không có quyền truy cập tài nguyên này", errorCode.ACCESS_DENIED.name()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<apiResponse<Void>> handleEntityNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(apiResponse.error(exception.getMessage(), errorCode.RESOURCE_NOT_FOUND.name()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<apiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(apiResponse.error("Không tìm thấy tài nguyên", errorCode.RESOURCE_NOT_FOUND.name()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<apiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(apiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Dữ liệu không hợp lệ")
                        .errorCode(errorCode.VALIDATION_ERROR.name())
                        .data(errors)
                        .timestamp(java.time.OffsetDateTime.now())
                        .build());
    }

    // Tham số truy vấn / đường dẫn sai kiểu (ví dụ giá trị enum không tồn tại) hoặc thiếu tham số bắt buộc là lỗi của người gọi (400), không phải lỗi hệ thống.
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<apiResponse<Void>> handleBadRequestParameter(Exception exception) {
        String name = exception instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : ((MissingServletRequestParameterException) exception).getParameterName();
        return ResponseEntity.badRequest()
                .body(apiResponse.error("Tham số không hợp lệ: " + name, errorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<apiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(apiResponse.error(exception.getMessage(), errorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<apiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(apiResponse.error("Dữ liệu gửi lên không hợp lệ", errorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<apiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(apiResponse.error("Thao tác xung đột với dữ liệu hiện có", errorCode.CONFLICT.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<apiResponse<Void>> handleGenericException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiResponse.error("Hệ thống tạm thời không khả dụng", errorCode.INTERNAL_ERROR.name()));
    }
}
