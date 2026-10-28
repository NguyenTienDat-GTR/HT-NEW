package phucuong.ht.modules.deanery.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.OrganizationUnit;
import phucuong.ht.modules.diocese.model.entity.Diocese;

@Entity
@Table(
        name = "deanery",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_deanery_diocese_name", columnNames = {"diocese_id", "name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Deanery extends OrganizationUnit {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_id", nullable = false)
    private Diocese diocese;

    @Column(name = "association_name")
    private String associationName;
}
