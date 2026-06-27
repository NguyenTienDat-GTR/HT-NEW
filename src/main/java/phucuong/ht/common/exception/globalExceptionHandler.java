package phucuong.ht.common.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<apiResponse<Void>> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(apiResponse.error("Invalid username or password", errorCode.AUTHENTICATION_FAILED.name()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<apiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(apiResponse.error("You do not have permission to access this resource", errorCode.ACCESS_DENIED.name()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<apiResponse<Void>> handleEntityNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(apiResponse.error(exception.getMessage(), errorCode.RESOURCE_NOT_FOUND.name()));
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
                        .message("Validation failed")
                        .errorCode(errorCode.VALIDATION_ERROR.name())
                        .data(errors)
                        .timestamp(java.time.OffsetDateTime.now())
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<apiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(apiResponse.error(exception.getMessage(), errorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<apiResponse<Void>> handleGenericException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiResponse.error(exception.getMessage(), errorCode.INTERNAL_ERROR.name()));
    }
}
