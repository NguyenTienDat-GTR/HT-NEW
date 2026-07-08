package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import phucuong.ht.common.model.BaseModel;

import java.util.UUID;

@Entity
@Table(
        name = "api_permission_policy",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_api_permission_policy_route_permission",
                        columnNames = {"http_method", "path_pattern", "permission_code"}
                )
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class ApiPermissionPolicy extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "path_pattern", nullable = false, length = 255)
    private String pathPattern;

    @Column(name = "handler_name", length = 255)
    private String handlerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_code", nullable = false)
    private Permission permission;

    @Column(name = "scope_strategy", nullable = false, length = 50)
    private String scopeStrategy;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;
}
