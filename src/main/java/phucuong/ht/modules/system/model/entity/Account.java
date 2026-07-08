package phucuong.ht.modules.system.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.model.entity.Parish;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id")
    private Diocese diocese;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id")
    private Deanery deanery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parish_id")
    private Parish parish;

    private String authProvider;

    private String providerId;

    private String avatarUrl;
}
