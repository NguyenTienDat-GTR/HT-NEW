package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionDelegationTest {

    @Test
    void dioceseAdminHoldingThePastCoursePermissionMayPassTheDeaneryLevelOneOn() {
        assertTrue(PermissionDelegation.canDelegate(List.of("ROLE_ADMIN_DIOCESE", "training.course.create_past.diocese"),
                "training.course.create_past.deanery"));
    }

    @Test
    void doesNotExtendToOtherPermissionsOrToOtherHolders() {
        assertFalse(PermissionDelegation.canDelegate(List.of("training.course.create_past.diocese"), "training.course.create.deanery"));
        assertFalse(PermissionDelegation.canDelegate(List.of("training.course.create_past.diocese"), "training.course.create_past.diocese"));
        assertFalse(PermissionDelegation.canDelegate(List.of("training.course.create_past.deanery"), "training.course.create_past.deanery"));
        assertFalse(PermissionDelegation.canDelegate(List.of("training.course.create_past.deanery"), "training.course.create_past.diocese"));
        assertFalse(PermissionDelegation.canDelegate(List.of(), "training.course.create_past.deanery"));
        assertFalse(PermissionDelegation.canDelegate(List.of("training.course.create_past.diocese"), null));
    }
}
