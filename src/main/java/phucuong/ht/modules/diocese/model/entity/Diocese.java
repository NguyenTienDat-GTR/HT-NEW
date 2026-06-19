package phucuong.ht.modules.diocese.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.common.model.OrganizationUnit;

import java.time.LocalDate;

@Entity
@Table(name = "diocese")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Diocese extends OrganizationUnit {

    @Id
    @Column(name = "diocese_code", length = 20)
    @GeneratedValue(strategy = GenerationType.UUID)
    private String dioceseCode;

    @Column(name = "union_name", nullable = false, length = 100)
    private String unionName;

}