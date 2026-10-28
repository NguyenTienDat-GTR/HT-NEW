package phucuong.ht.modules.parish.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.OrganizationUnit;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;

@Entity
@Table(
        name = "parish",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_parish_deanery_name", columnNames = {"deanery_id", "name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Parish extends OrganizationUnit {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_id", nullable = false)
    private Deanery deanery;

    @Column(name = "chapter_name", nullable = false)
    private String chapterName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20)
    private ParishUnitType unitType = ParishUnitType.PARISH;

    // Chỉ có với giáo họ / giáo điểm: giáo xứ cha.
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_parish_id")
    private Parish parentParish;
}
