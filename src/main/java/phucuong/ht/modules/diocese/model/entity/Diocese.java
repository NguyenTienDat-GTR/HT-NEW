package phucuong.ht.modules.diocese.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import phucuong.ht.common.model.OrganizationUnit;

@Entity
@Table(
        name = "diocese",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_diocese_name", columnNames = "name")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class Diocese extends OrganizationUnit {

    @Column(name = "union_name", nullable = false, length = 100)
    private String unionName;

    /** Nhãn subdomain của giáo phận (không gồm tên miền chính); null nếu giáo phận không có trang riêng. */
    @Column(name = "site_subdomain", length = 63)
    private String siteSubdomain;

}
