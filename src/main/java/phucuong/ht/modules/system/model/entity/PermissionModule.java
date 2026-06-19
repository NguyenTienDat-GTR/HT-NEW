package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "permission_module")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class PermissionModule extends BaseModel {

    @Id
    @Column(name = "module_code")
    private String moduleCode;

    private String moduleName;

    private String description;

    private Integer displayOrder;
}
