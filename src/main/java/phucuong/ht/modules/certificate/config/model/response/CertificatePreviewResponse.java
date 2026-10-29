package phucuong.ht.modules.certificate.config.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Xem trước một chứng nhận: cấu hình đang áp dụng (khổ giấy, ảnh nền) và các phần tử đã điền dữ liệu thật,
// dựng bằng đúng cấu hình + dữ liệu như khi xuất PDF.
@Getter
@Builder
public class CertificatePreviewResponse {

    // false khi chưa có cấu hình chứng nhận (riêng hoặc chung) cho khóa của chứng nhận này.
    private boolean configured;
    private String paperSize;
    private String orientation;
    private String backgroundImage;
    private List<Item> items;

    // type = TEXT: phần tử chữ đã điền dữ liệu; type = QR: mã QR chứa mã chứng nhận, image là ảnh SVG (data URL)
    // sinh tại thời điểm xem trước (không lưu), size là cạnh theo tỉ lệ chiều cao trang, x,y là tâm.
    public record Item(String type, String text, double x, double y, double fontSize, String color, String align,
                       String fontFamily, boolean bold, boolean italic, boolean underline, Double size, String image, Double aspect) {
        public Item(String type, String text, double x, double y, double fontSize, String color, String align,
                    String fontFamily, boolean bold, boolean italic, boolean underline, Double size, String image) {
            this(type, text, x, y, fontSize, color, align, fontFamily, bold, italic, underline, size, image, null);
        }
    }
}
