package phucuong.ht.modules.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.modules.auth.service.SiteHostPolicy;

/**
 * Endpoint nội bộ cho Caddy (on_demand_tls ask): 200 nếu được cấp chứng chỉ cho tên miền, 404 nếu không.
 * Đường dẫn nằm ngoài /api nên FE không proxy tới được, và Caddy chỉ chuyển /ws/* vào BE.
 */
@RestController
@RequestMapping("/internal/site-hosts")
@RequiredArgsConstructor
public class SiteHostController {

    private final SiteHostPolicy siteHostPolicy;

    @GetMapping("/check")
    public ResponseEntity<Void> check(@RequestParam("domain") String domain, HttpServletRequest request) {
        // Request của người dùng đi qua Caddy luôn mang X-Site-Host; lời gọi hợp lệ của Caddy thì không.
        if (request.getHeader("X-Site-Host") != null) {
            return ResponseEntity.notFound().build();
        }
        return siteHostPolicy.isServableDioceseHost(domain)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }
}
