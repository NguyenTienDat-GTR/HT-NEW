package phucuong.ht.modules.system.service;

import phucuong.ht.common.validation.CommonValidationHelper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Quy tắc kết hợp vai trò trên một tài khoản: tối đa {@value #MAX_ROLES_PER_ACCOUNT} vai trò, và SUPER_ADMIN / ADMIN_DIOCESE
 * phải là vai trò duy nhất. ADMIN_DEANERY, ADMIN_PARISH và các vai trò tùy chỉnh trong phạm vi được kết hợp với nhau.
 */
final class AccountRoleCombinationPolicy {

    static final int MAX_ROLES_PER_ACCOUNT = 3;
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ADMIN_DIOCESE = "ADMIN_DIOCESE";

    private AccountRoleCombinationPolicy() {
    }

    /** {@code resultingRoleCodes}: các vai trò đang hiệu lực của tài khoản sau thay đổi (đã gồm vai trò mới, bỏ trùng). */
    static void require(Collection<String> resultingRoleCodes, CommonValidationHelper validation) {
        Set<String> roles = new LinkedHashSet<>(resultingRoleCodes);
        if (roles.size() > MAX_ROLES_PER_ACCOUNT) {
            throw validation.validationError("Mỗi tài khoản tối đa " + MAX_ROLES_PER_ACCOUNT + " vai trò");
        }
        if (roles.size() > 1 && (roles.contains(SUPER_ADMIN) || roles.contains(ADMIN_DIOCESE))) {
            throw validation.validationError("Tài khoản SUPER_ADMIN hoặc ADMIN_DIOCESE chỉ được gán một vai trò");
        }
    }
}
