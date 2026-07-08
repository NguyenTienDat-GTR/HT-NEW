package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.common.enums.UnitType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class SystemLogResponse {
    private String id;
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
    private String message;
    private String errorMessage;
    private OffsetDateTime loggedAt;
    private UnitType unitType;
    private UUID unitId;
    private UUID dioceseId;
    private UUID deaneryId;
    private UUID parishId;
}
