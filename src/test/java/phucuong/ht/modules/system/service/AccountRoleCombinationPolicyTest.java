package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRoleCombinationPolicyTest {

    private final CommonValidationHelper validation = new CommonValidationHelper(new securityUtils());

    @Test
    void allowsDeaneryParishAndCustomRolesTogetherUpToThree() {
        assertDoesNotThrow(() -> AccountRoleCombinationPolicy.require(List.of("ADMIN_DEANERY", "ADMIN_PARISH", "CUSTOM_ROLE"), validation));
    }

    @Test
    void rejectsMoreThanThreeRoles() {
        assertThrows(ApiException.class,
                () -> AccountRoleCombinationPolicy.require(List.of("ADMIN_DEANERY", "ADMIN_PARISH", "CUSTOM_A", "CUSTOM_B"), validation));
    }

    @Test
    void duplicatesAreCountedOnce() {
        assertDoesNotThrow(() -> AccountRoleCombinationPolicy.require(List.of("ADMIN_PARISH", "ADMIN_PARISH", "CUSTOM_A"), validation));
    }

    @Test
    void dioceseAdminAndSuperAdminMustBeTheOnlyRole() {
        assertDoesNotThrow(() -> AccountRoleCombinationPolicy.require(List.of("ADMIN_DIOCESE"), validation));
        assertDoesNotThrow(() -> AccountRoleCombinationPolicy.require(List.of("SUPER_ADMIN"), validation));
        assertThrows(ApiException.class, () -> AccountRoleCombinationPolicy.require(List.of("ADMIN_DIOCESE", "ADMIN_PARISH"), validation));
        assertThrows(ApiException.class, () -> AccountRoleCombinationPolicy.require(List.of("CUSTOM_A", "SUPER_ADMIN"), validation));
    }
}
