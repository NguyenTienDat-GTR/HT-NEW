package phucuong.ht.modules.certificate.config.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// Một phần tử đặt trên ảnh nền chứng nhận.
// type = FIELD: lấy giá trị theo fieldKey (bộ trường cố định); type = TEXT: in nguyên văn trường text;
// type = QR: mã QR chứa mã chứng nhận, chỉ sinh lúc xuất PDF / xem trước (không lưu ảnh QR).
// Tọa độ x,y và fontSize là tỉ lệ 0..1 so với kích thước ảnh nền để độc lập độ phân giải.
@Getter
@Setter
@ToString
public class CertificateLayoutItem {

    /** Tỉ lệ rộng/cao của ô ảnh huynh trưởng theo khổ đã chọn. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public double photoAspect() {
        String ratio = photoRatio == null ? "4x6" : photoRatio.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (ratio) {
            case "3x4" -> 3.0 / 4.0;
            case "free" -> {
                double height = size == null || size <= 0 ? 0.25 : size;
                double width = photoWidth == null || photoWidth <= 0 ? height * 2.0 / 3.0 : photoWidth;
                yield Math.min(3.0, Math.max(0.2, width / height));
            }
            default -> 2.0 / 3.0;
        };
    }


    // "FIELD", "TEXT", "QR" hoặc "PHOTO" (ảnh đại diện của huynh trưởng, size = cạnh ô vuông theo tỉ lệ chiều cao).
    private String type;

    // Khi type = FIELD: mã trường trong catalog.
    private String fieldKey;

    // Khi type = TEXT: nội dung tĩnh.
    private String text;

    // Tỉ lệ 0..1 theo chiều rộng/cao ảnh nền (gốc toạ độ góc trên-trái).
    private Double x;
    private Double y;

    // Khi type = QR: cạnh mã QR theo tỉ lệ chiều cao trang (0..1); x,y là tâm mã QR.
    private Double size;

    // Khi type = PHOTO: khổ ảnh "4x6" (rộng:cao = 2:3), "3x4" hoặc "free" (tự kéo giãn, dùng photoWidth).
    // size là chiều CAO ảnh theo tỉ lệ chiều cao trang; mặc định 4x6.
    private String photoRatio;

    // Khi photoRatio = "free": chiều RỘNG ảnh theo tỉ lệ chiều cao trang (0..1).
    private Double photoWidth;

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

    // Chỉ dùng trong trình soạn: khóa phần tử để khỏi kéo nhầm (vẫn in bình thường).
    private Boolean locked;

    // Ẩn phần tử: không hiện trong trình soạn xem trước và không in ra PDF.
    private Boolean hidden;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPrinted() {
        return !Boolean.TRUE.equals(hidden);
    }
}
