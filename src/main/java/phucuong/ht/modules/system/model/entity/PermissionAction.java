package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

@Entity
@Table(name = "permission_action")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class PermissionAction extends BaseModel {

    @Id
    @Column(name = "action_code")
    private String actionCode;

    private String actionName;

    private String description;

    private Integer displayOrder;
}