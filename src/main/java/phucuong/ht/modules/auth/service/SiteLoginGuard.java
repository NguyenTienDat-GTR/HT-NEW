package phucuong.ht.modules.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Giới hạn nơi đăng nhập theo subdomain: tài khoản thuộc một giáo phận chỉ đăng nhập được ở subdomain
 * của giáo phận đó. Tài khoản không gắn giáo phận (cấp toàn hệ thống) và các tên miền không gắn giáo phận
 * (tên miền chính, staging, test) không bị giới hạn.
 */
@Component
public class SiteLoginGuard {

    private final String baseDomain;
    private final AccountRepository accountRepository;
    private final DioceseRepository dioceseRepository;

    public SiteLoginGuard(
            @Value("${app.site.base-domain:}") String baseDomain,
            AccountRepository accountRepository,
            DioceseRepository dioceseRepository
    ) {
        this.baseDomain = baseDomain == null ? "" : baseDomain.trim().toLowerCase(Locale.ROOT);
        this.accountRepository = accountRepository;
        this.dioceseRepository = dioceseRepository;
    }

    /** Gọi sau khi mật khẩu đã đúng, trước khi cấp token. siteHost lấy từ header do reverse proxy đặt. */
    public void assertCanLogin(String username, String siteHost) {
        Optional<String> subdomain = subdomainOf(siteHost);
        if (subdomain.isEmpty()) {
            return;
        }
        Optional<Diocese> site = dioceseRepository.findBySiteSubdomain(subdomain.get());
        if (site.isEmpty()) {
            return;
        }
        UUID accountDioceseId = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        if (accountDioceseId != null && !accountDioceseId.equals(site.get().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED,
                    "Tài khoản không thuộc giáo phận của trang này, vui lòng đăng nhập ở trang của giáo phận mình");
        }
    }

    public String baseDomain() {
        return baseDomain;
    }

    public Optional<String> subdomainOf(String siteHost) {
        if (baseDomain.isEmpty() || !StringUtils.hasText(siteHost)) {
            return Optional.empty();
        }
        String host = siteHost.trim().toLowerCase(Locale.ROOT);
        int portIndex = host.indexOf(':');
        if (portIndex >= 0) {
            host = host.substring(0, portIndex);
        }
        String suffix = "." + baseDomain;
        if (!host.endsWith(suffix)) {
            return Optional.empty();
        }
        String label = host.substring(0, host.length() - suffix.length());
        if (label.isEmpty() || label.contains(".")) {
            return Optional.empty();
        }
        return Optional.of(label);
    }
}
