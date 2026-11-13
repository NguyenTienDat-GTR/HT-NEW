package phucuong.ht.modules.training.courseType.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Loại khóa do giáo phận tạo giáo hạt chỉ được dùng; loại giáo hạt tự tạo chỉ giáo hạt đó thấy và sửa. */
class CourseTypeOwnershipTest {

    private static final UUID DIOCESE = UUID.randomUUID();
    private static final UUID DEANERY_A = UUID.randomUUID();
    private static final UUID DEANERY_B = UUID.randomUUID();

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CourseTypeOwnership ownership = new CourseTypeOwnership(new securityUtils(), accounts, new CommonValidationHelper(new securityUtils()));

    private final TrainingCourseType shared = type(UnitType.DEANERY, UnitType.DIOCESE, null);
    private final TrainingCourseType ownA = type(UnitType.DEANERY, UnitType.DEANERY, DEANERY_A);
    private final TrainingCourseType dioceseLevel = type(UnitType.DIOCESE, UnitType.DIOCESE, null);

    private Authentication dioceseAccount() {
        when(accounts.findActiveDioceseIdByUsername("gp")).thenReturn(Optional.of(DIOCESE));
        return auth("gp", "training.course_type.create.diocese", "training.course_type.read.diocese", "training.course_type.update.diocese", "training.course_type.create.deanery");
    }

    private Authentication deaneryAccount(String username, UUID deanery) {
        when(accounts.findActiveDioceseIdByUsername(username)).thenReturn(Optional.of(DIOCESE));
        when(accounts.findActiveDeaneryIdByUsername(username)).thenReturn(Optional.of(deanery));
        return auth(username, "training.course_type.create.deanery", "training.course_type.read.deanery", "training.course_type.update.deanery");
    }

    @Test
    void newDeaneryLevelTypeBelongsToCreatorScope() {
        var owner = ownership.ownerForNewType(UnitType.DEANERY, deaneryAccount("a", DEANERY_A));
        assertEquals(UnitType.DEANERY, owner.unit());
        assertEquals(DEANERY_A, owner.deaneryId());
        assertEquals(CourseTypeOwnership.Owner.DIOCESE, ownership.ownerForNewType(UnitType.DEANERY, dioceseAccount()));
        assertEquals(CourseTypeOwnership.Owner.DIOCESE, ownership.ownerForNewType(UnitType.DIOCESE, deaneryAccount("a2", DEANERY_A)));
    }

    @Test
    void deaneryCannotEditDioceseProvidedTypesButCanEditItsOwn() {
        Authentication a = deaneryAccount("a", DEANERY_A);
        assertFalse(ownership.canManage(shared, a));
        assertFalse(ownership.canManage(dioceseLevel, a));
        assertTrue(ownership.canManage(ownA, a));
        assertFalse(ownership.canManage(ownA, deaneryAccount("b", DEANERY_B)));
        assertThrows(ApiException.class, () -> ownership.requireManageable(shared, a));
    }

    @Test
    void dioceseManagesItsTypesButNotDeaneryOwnedOnes() {
        Authentication gp = dioceseAccount();
        assertTrue(ownership.canManage(shared, gp));
        assertTrue(ownership.canManage(dioceseLevel, gp));
        assertFalse(ownership.canManage(ownA, gp));
        assertFalse(ownership.canSee(ownA, gp));
        assertTrue(ownership.canSee(shared, gp));
    }

    @Test
    void deaneriesSeeSharedAndOwnTypesOnly() {
        Authentication b = deaneryAccount("b", DEANERY_B);
        assertTrue(ownership.canSee(shared, b));
        assertFalse(ownership.canSee(ownA, b));
        assertTrue(ownership.canSee(ownA, deaneryAccount("a", DEANERY_A)));
    }

    @Test
    void unscopedAccountSeesAndManagesEverything() {
        when(accounts.findActiveDioceseIdByUsername("root")).thenReturn(Optional.empty());
        Authentication root = auth("root");
        assertTrue(ownership.canSee(ownA, root));
        assertTrue(ownership.canManage(ownA, root));
    }

    @Test
    void listScopeFollowsTheAccount() {
        BaseSearchRequest forDiocese = ownership.scopeList(new BaseSearchRequest(), dioceseAccount());
        assertEquals(1, forDiocese.getScopeAnyOf().size());
        BaseSearchRequest forDeanery = ownership.scopeList(new BaseSearchRequest(), deaneryAccount("a", DEANERY_A));
        assertEquals(2, forDeanery.getScopeAnyOf().size());
        assertEquals(DEANERY_A, forDeanery.getScopeAnyOf().get(1).get("ownerDeaneryId"));
    }

    @Test
    void scopeIsLookedUpOncePerRequestEvenForWriteRequests() {
        Authentication a = deaneryAccount("a", DEANERY_A);
        bindRequest("PUT");
        try {
            assertTrue(ownership.canSee(ownA, a));
            assertTrue(ownership.canManage(ownA, a));
            ownership.requireManageable(ownA, a);
            assertEquals(DEANERY_A, ownership.ownerForNewType(UnitType.DEANERY, a).deaneryId());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        verify(accounts, times(1)).findActiveDioceseIdByUsername("a");
        verify(accounts, times(1)).findActiveDeaneryIdByUsername("a");
    }

    @Test
    void scopeOfDifferentAccountsIsNotMixedWithinOneRequest() {
        Authentication a = deaneryAccount("a", DEANERY_A);
        Authentication b = deaneryAccount("b", DEANERY_B);
        bindRequest("PUT");
        try {
            assertTrue(ownership.canManage(ownA, a));
            assertFalse(ownership.canManage(ownA, b));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static void bindRequest(String method) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(method, "/api/training/course-types/x")));
    }

    private static Authentication auth(String username, String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of(), List.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static TrainingCourseType type(UnitType level, UnitType owner, UUID deanery) {
        TrainingCourseType t = new TrainingCourseType();
        t.setTypeCode(UUID.randomUUID().toString());
        t.setManagementUnitType(level);
        t.setOwnerUnitType(owner);
        t.setOwnerDeaneryId(deanery);
        t.setDioceseId(DIOCESE);
        t.setStatus(true);
        return t;
    }
}
