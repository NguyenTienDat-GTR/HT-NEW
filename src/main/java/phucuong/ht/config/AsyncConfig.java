package phucuong.ht.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Bật xử lý bất đồng bộ; executor riêng cho việc sinh PDF chứng nhận (có thể hàng trăm chứng nhận).
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "certificatePdfExecutor")
    public Executor certificatePdfExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("cert-pdf-");
        executor.initialize();
        return executor;
    }

    // Executor gửi email thông tin tham dự khóa huấn luyện (mỗi thư vài trăm ms, cả khóa có thể hàng trăm thư).
    @Bean(name = "invitationMailExecutor")
    public Executor invitationMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("invitation-mail-");
        executor.initialize();
        return executor;
    }

    // Executor riêng sinh mã QR chứng nhận, chạy song song với việc chuẩn bị dữ liệu PDF.
    // Hàng đợi đầy thì luồng gọi tự chạy (CallerRuns) để không bao giờ bỏ sót mã QR của chứng nhận nào.
    @Bean(name = "certificateQrExecutor")
    public Executor certificateQrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()));
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("cert-qr-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    public static final String AUDIT_LOG_EXECUTOR = "auditLogExecutor";

    // Executor lưu nhật ký API (system_log) sau khi đã trả phản hồi, để việc tra phạm vi và insert không cộng vào thời gian request.
    // Hàng đợi đầy thì luồng gọi tự lưu (CallerRuns) và khi tắt ứng dụng thì chờ lưu hết: không bỏ sót dòng nhật ký nào.
    @Bean(name = AUDIT_LOG_EXECUTOR)
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
