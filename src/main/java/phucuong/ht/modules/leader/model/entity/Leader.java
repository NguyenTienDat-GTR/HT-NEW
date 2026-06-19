package phucuong.ht.modules.leader.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.parish.model.entity.Parish;

import java.time.LocalDate;

@Entity
@Table(name = "leader")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Leader extends BaseModel {

    @Id
    @Column(name = "leader_code", length = 20)
    @GeneratedValue(strategy = GenerationType.UUID)
    private String leaderCode;

    @Column(name = "holy_name", nullable = false, length = 50)
    private String holyName;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "leader_level", nullable = false)
    private LeaderLevel leaderLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parish_code", nullable = false)
    private Parish parish;
}