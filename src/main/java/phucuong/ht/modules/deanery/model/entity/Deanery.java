package phucuong.ht.modules.deanery.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.OrganizationUnit;
import phucuong.ht.modules.diocese.model.entity.Diocese;

@Entity
@Table(name = "deanery")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Deanery extends OrganizationUnit {

    @Id
    @Column(name = "deanery_code", length = 50)
    private String deaneryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diocese_code", nullable = false)
    private Diocese diocese;

    @Column(name = "association_name", nullable = false)
    private String associationName;
}
