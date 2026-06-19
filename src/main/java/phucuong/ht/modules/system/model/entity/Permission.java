package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "permission")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Permission extends BaseModel {

    @Id
    @Column(name = "permission_code")
    private String permissionCode;

    private String permissionName;

    @ManyToOne
    @JoinColumn(name = "module_code")
    private PermissionModule module;

    @ManyToOne
    @JoinColumn(name = "resource_code")
    private PermissionResource resource;

    @ManyToOne
    @JoinColumn(name = "action_code")
    private PermissionAction action;

    @ManyToOne
    @JoinColumn(name = "scope_code")
    private PermissionScope scope;

    private String description;

    private Boolean isSystem;

    private Integer displayOrder;
}
