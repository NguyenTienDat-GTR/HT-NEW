package phucuong.ht.modules.auth.service;

import org.springframework.stereotype.Component;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quyết định tên miền nào được cấp HTTPS và phục vụ như trang riêng của giáo phận: subdomain một cấp dưới tên miền
 * chính, trùng site_subdomain của một giáo phận đang hoạt động. Caddy hỏi điều này trước khi xin chứng chỉ và trước mỗi
 * request tới trang giáo phận, nên kết quả được nhớ ngắn hạn; cấp/thu hồi subdomain gọi {@link #invalidate()}.
 */
@Component
public class SiteHostPolicy {

    /** Các nhãn có việc riêng (môi trường test, thư...) không được dùng làm trang giáo phận. */
    public static final Set<String> RESERVED_LABELS = Set.of("www", "test", "staging", "api", "mail", "admin");

    private static final long CACHE_TTL_NANOS = 15_000_000_000L;

    private record Decision(boolean servable, long expiresAtNanos) {
    }

    private final SiteLoginGuard siteLoginGuard;
    private final DioceseRepository dioceseRepository;
    private final ConcurrentHashMap<String, Decision> cache = new ConcurrentHashMap<>();

    public SiteHostPolicy(SiteLoginGuard siteLoginGuard, DioceseRepository dioceseRepository) {
        this.siteLoginGuard = siteLoginGuard;
        this.dioceseRepository = dioceseRepository;
    }

    /** Tên miền chính đã cấu hình; rỗng nghĩa là tính năng trang riêng chưa được bật. */
    public String baseDomain() {
        return siteLoginGuard.baseDomain();
    }

    public boolean isServableDioceseHost(String domain) {
        Optional<String> subdomain = siteLoginGuard.subdomainOf(domain);
        if (subdomain.isEmpty() || RESERVED_LABELS.contains(subdomain.get())) {
            return false;
        }
        long now = System.nanoTime();
        Decision cached = cache.get(subdomain.get());
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.servable();
        }
        boolean servable = dioceseRepository.findBySiteSubdomain(subdomain.get())
                .map(Diocese::getStatus)
                .map(Boolean.TRUE::equals)
                .orElse(false);
        if (cache.size() > 1000) {
            cache.clear();
        }
        cache.put(subdomain.get(), new Decision(servable, now + CACHE_TTL_NANOS));
        return servable;
    }

    public void invalidate() {
        cache.clear();
    }
}
