package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.model.BaseModel;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_log")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class SystemLog extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "username")
    private String username;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "module", length = 100)
    private String module;

    @Column(name = "resource", length = 150)
    private String resource;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "logged_at", nullable = false)
    private OffsetDateTime loggedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "diocese_id")
    private UUID dioceseId;

    @Column(name = "deanery_id")
    private UUID deaneryId;

    @Column(name = "parish_id")
    private UUID parishId;
}
