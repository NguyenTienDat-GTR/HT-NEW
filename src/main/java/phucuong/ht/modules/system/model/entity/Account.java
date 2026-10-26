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

    /** Email liên hệ cho tài khoản không gắn Huynh Trưởng; tài khoản gắn Huynh Trưởng dùng email của Huynh Trưởng. */
    private String email;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = Boolean.FALSE;

    /** Hạn dùng của mật khẩu tạm do admin đặt lại; null = mật khẩu không có hạn (tài khoản mới tạo, mật khẩu thường). */
    @Column(name = "temporary_password_expires_at")
    private java.time.OffsetDateTime temporaryPasswordExpiresAt;
}
