package phucuong.ht.modules.auth.service;

import org.springframework.stereotype.Component;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Optional;
import java.util.Set;

/**
 * Quyết định tên miền nào được cấp HTTPS và phục vụ như trang riêng của giáo phận: subdomain một cấp dưới tên miền
 * chính, trùng site_subdomain của một giáo phận đang hoạt động. Caddy hỏi điều này trước khi xin chứng chỉ, nên
 * thêm giáo phận mới chỉ cần gán site_subdomain trong database.
 */
@Component
public class SiteHostPolicy {

    /** Các nhãn có việc riêng (môi trường test, thư...) không được dùng làm trang giáo phận. */
    static final Set<String> RESERVED_LABELS = Set.of("www", "test", "staging", "api", "mail", "admin");

    private final SiteLoginGuard siteLoginGuard;
    private final DioceseRepository dioceseRepository;

    public SiteHostPolicy(SiteLoginGuard siteLoginGuard, DioceseRepository dioceseRepository) {
        this.siteLoginGuard = siteLoginGuard;
        this.dioceseRepository = dioceseRepository;
    }

    public boolean isServableDioceseHost(String domain) {
        Optional<String> subdomain = siteLoginGuard.subdomainOf(domain);
        if (subdomain.isEmpty() || RESERVED_LABELS.contains(subdomain.get())) {
            return false;
        }
        return dioceseRepository.findBySiteSubdomain(subdomain.get())
                .map(Diocese::getStatus)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }
}
