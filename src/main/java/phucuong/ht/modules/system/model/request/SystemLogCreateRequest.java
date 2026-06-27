package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class SystemLogCreateRequest {

    private String username;

    private String action;

    private String module;

    private String resource;

    private String resourceId;

    private String httpMethod;

    private String requestPath;

    private Integer statusCode;

    private String ipAddress;

    private String userAgent;

    private String requestBody;

    private String responseBody;

    private String message;

    private String errorMessage;

    @NotNull
    private OffsetDateTime loggedAt;
}
