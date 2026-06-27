package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;

@Entity
@Table(name = "account")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Account extends BaseModel {

    @Id
    private String username;

    private String password;

    @OneToOne
    @JoinColumn(name = "leader_id")
    private Leader leader;

    private String authProvider;

    private String providerId;

    private String avatarUrl;
}
