package phucuong.ht.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Kiểm tra quyền (authority) của người đang đăng nhập; dùng chung thay cho các bản private lặp ở service/evaluator. */
public final class AuthorityChecks {

    private AuthorityChecks() {
    }

    public static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authority == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
