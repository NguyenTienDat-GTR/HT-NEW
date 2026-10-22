package phucuong.ht.modules.auth.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.modules.system.model.entity.Account;

/** Xác định email nhận OTP: ưu tiên email Huynh Trưởng, sau đó email lưu trên tài khoản. */
@Component
public class AccountEmailResolver {

    public String resolve(Account account) {
        if (account == null) {
            return null;
        }
        if (account.getLeader() != null && StringUtils.hasText(account.getLeader().getEmail())) {
            return account.getLeader().getEmail().trim();
        }
        return StringUtils.hasText(account.getEmail()) ? account.getEmail().trim() : null;
    }
}
