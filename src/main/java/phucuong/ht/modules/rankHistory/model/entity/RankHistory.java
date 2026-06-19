package phucuong.ht.modules.rankHistory.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;

@Entity
@Table(name = "rank_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class RankHistory extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "leader_id")
    private Leader leader;

    @Enumerated(EnumType.STRING)
    private LeaderLevel oldLevel;

    @Enumerated(EnumType.STRING)
    private LeaderLevel newLevel;

    private LocalDate promotionDate;

    private String note;

    private String updatedBy;
}
