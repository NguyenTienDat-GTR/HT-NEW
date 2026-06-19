package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "permission_scope")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class PermissionScope extends BaseModel {

    @Id
    @Column(name = "scope_code")
    private String scopeCode;

    private String scopeName;

    private String description;

    private Integer displayOrder;
}
