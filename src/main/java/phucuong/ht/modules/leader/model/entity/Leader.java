package phucuong.ht.modules.leader.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.parish.model.entity.Parish;

import java.time.LocalDate;
import java.util.UUID;

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
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "holy_name", nullable = false, length = 50)
    private String holyName;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    // Dữ liệu ảnh base64 cũ, chỉ để job chuyển lên cloud đọc; ảnh hiện tại nằm ở media_asset (imageAssetId).
    @Column(name = "image_url")
    @ToString.Exclude
    private String legacyImageData;

    @Column(name = "image_asset_id")
    private UUID imageAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private LeaderGender gender;

    @Column(name = "leader_level", nullable = false)
    private String leaderLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parish_id", nullable = false)
    private Parish parish;
}
