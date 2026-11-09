package phucuong.ht.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public class OrganizationUnit extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Ngày bổn mạng chỉ gồm ngày và tháng (không có năm), lưu dạng "MM-dd". */
    @Convert(converter = MonthDayConverter.class)
    @Column(name = "patron_saint_day", length = 5)
    private MonthDay patronSaintDay;

    @Column(name = "establishment_date")
    private LocalDate establishmentDate;

    @Column(name = "chaplain", length = 100)
    private String chaplain;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "email", length = 100)
    private String email;
}