package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "permission_resource")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class PermissionResource extends BaseModel {

    @Id
    @Column(name = "resource_code")
    private String resourceCode;

    @ManyToOne
    @JoinColumn(name = "module_code")
    private PermissionModule module;

    private String resourceName;

    private String description;

    private Integer displayOrder;
}
