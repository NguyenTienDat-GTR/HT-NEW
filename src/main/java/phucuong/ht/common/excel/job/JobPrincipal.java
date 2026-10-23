package phucuong.ht.common.excel.job;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import java.util.List;

/**
 * Ảnh chụp người tạo job (tên đăng nhập, mã huynh trưởng, toàn bộ quyền) lưu cùng job. Job chạy nền không có request nên dựng lại
 * {@link Authentication} từ ảnh chụp này; mọi kiểm tra phạm vi trong service vẫn áp dụng như khi người dùng tự gọi.
 */
public record JobPrincipal(String username, String leaderId, List<String> authorities) {

    public static JobPrincipal of(Authentication authentication, String username, String leaderId) {
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).distinct().toList();
        return new JobPrincipal(username, leaderId, authorities);
    }

    public Authentication toAuthentication() {
        List<String> roles = authorities.stream().filter(authority -> authority.startsWith("ROLE_")).toList();
        List<String> permissions = authorities.stream().filter(authority -> !authority.startsWith("ROLE_")).toList();
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
