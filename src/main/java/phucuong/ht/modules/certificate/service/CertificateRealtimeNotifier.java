package phucuong.ht.modules.certificate.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;

/**
 * Phát tín hiệu realtime sau khi commit để màn chứng nhận và tab duyệt chứng nhận của khóa tải lại danh sách.
 * Chỉ gửi tín hiệu (không kèm dữ liệu); client tự gọi lại API nên vẫn bị kiểm soát quyền như bình thường.
 */
@Component
public class CertificateRealtimeNotifier {

    public static final String DESTINATION = "/topic/certificate-events";

    private final SimpMessagingTemplate simpMessagingTemplate;

    public CertificateRealtimeNotifier(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void notifyChanged(String reason, String courseCode, String certificateCode) {
        CertificateChangedEvent event = new CertificateChangedEvent("CERTIFICATE_CHANGED", reason, courseCode, certificateCode, OffsetDateTime.now());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    simpMessagingTemplate.convertAndSend(DESTINATION, event);
                }
            });
            return;
        }
        simpMessagingTemplate.convertAndSend(DESTINATION, event);
    }

    public record CertificateChangedEvent(String type, String reason, String courseCode, String certificateCode, OffsetDateTime occurredAt) {
    }
}
