package phucuong.ht.modules.parish.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.OrganizationUnit;
import phucuong.ht.modules.deanery.model.entity.Deanery;

@Entity
@Table(name = "parish")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Parish extends OrganizationUnit {

    @Id
    @Column(name = "parish_code", length = 20)
    @GeneratedValue(strategy = GenerationType.UUID)
    private String parishCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deanery_code", nullable = false)
    private Deanery deanery;

    @Column(name = "chapter_name", nullable = false)
    private String chapterName;
}