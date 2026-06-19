package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "role")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseModel {

    @Id
    @Column(name = "role_code")
    private String roleCode;

    private String roleName;

    private String description;

    private Boolean isSystem;

    private Boolean status;

    private Integer displayOrder;
}
