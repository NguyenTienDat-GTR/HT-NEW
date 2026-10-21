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

    public record Item(String text, double x, double y, double fontSize, String color, String align,
                       String fontFamily, boolean bold, boolean italic, boolean underline) {
    }
}
