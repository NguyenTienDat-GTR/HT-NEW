package phucuong.ht.modules.certificate.config.model.response;

import java.util.UUID;

// Loại khóa đang được gán rõ ràng cấu hình nào trong phạm vi của người xem (không gồm loại khóa dùng cấu hình chung).
public record CertificateConfigAssignmentResponse(String courseTypeCode, UUID configId) {
}
