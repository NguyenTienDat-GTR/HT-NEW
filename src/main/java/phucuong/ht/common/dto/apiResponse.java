package phucuong.ht.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class apiResponse<T> {

    private final boolean success;
    private final String message;
    private final String errorCode;
    private final T data;
    private final OffsetDateTime timestamp;

    public static <T> apiResponse<T> success(String message, T data) {
        return apiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static apiResponse<Void> error(String message, String errorCode) {
        return apiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
