package phucuong.ht.modules.certificate.config.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// Một phần tử đặt trên ảnh nền chứng nhận.
// type = FIELD: lấy giá trị theo fieldKey (bộ trường cố định); type = TEXT: in nguyên văn trường text.
// Tọa độ x,y và fontSize là tỉ lệ 0..1 so với kích thước ảnh nền để độc lập độ phân giải.
@Getter
@Setter
@ToString
public class CertificateLayoutItem {

    // "FIELD" hoặc "TEXT".
    private String type;

    // Khi type = FIELD: mã trường trong catalog.
    private String fieldKey;

    // Khi type = TEXT: nội dung tĩnh.
    private String text;

    // Tỉ lệ 0..1 theo chiều rộng/cao ảnh nền (gốc toạ độ góc trên-trái).
    private Double x;
    private Double y;

    // Cỡ chữ theo tỉ lệ so với chiều cao ảnh nền (0..1).
    private Double fontSize;

    // Màu dạng hex "#RRGGBB".
    private String color;

    // "left" | "center" | "right".
    private String align;

    // Họ chữ: "sans" | "serif".
    private String fontFamily;

    private Boolean bold;

    private Boolean italic;

    private Boolean underline;
}
