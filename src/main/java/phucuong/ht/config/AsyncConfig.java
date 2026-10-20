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
}
