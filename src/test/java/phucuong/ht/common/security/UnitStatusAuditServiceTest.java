package phucuong.ht.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.common.dto.UnitToggleImpactResponse;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnitStatusAuditServiceTest {

    private EntityManager entityManager;
    private Query query;
    private UnitOperationalStatusService operationalStatusService;
    private CommonValidationHelper validationHelper;
    private UnitStatusAuditService service;
    private final UUID unitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class, Mockito.RETURNS_SELF);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        operationalStatusService = mock(UnitOperationalStatusService.class);
        validationHelper = mock(CommonValidationHelper.class);
        when(validationHelper.validationError(anyString()))
                .thenAnswer(invocation -> new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, invocation.getArgument(0)));
        service = new UnitStatusAuditService(operationalStatusService, validationHelper);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void impactCountsAffectedAccountsAndMarksActiveUnit() {
        when(query.getSingleResult()).thenReturn(1L);
        when(operationalStatusService.findActiveUsernamesInSubtree(UnitType.DEANERY, unitId)).thenReturn(Set.of("a", "b", "c"));

        UnitToggleImpactResponse impact = service.impact(UnitType.DEANERY, unitId);

        assertTrue(impact.currentlyActive());
        assertEquals(3, impact.affectedAccounts());
        assertEquals("DEANERY", impact.unitType());
    }

    @Test
    void disablingActiveUnitWithoutReasonIsRejected() {
        when(query.getSingleResult()).thenReturn(1L);
        when(operationalStatusService.findActiveUsernamesInSubtree(UnitType.PARISH, unitId)).thenReturn(Set.of());

        ApiException exception = assertThrows(ApiException.class, () -> service.begin(UnitType.PARISH, unitId, "  "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void disablingWithReasonKeepsContextAndWritesHistory() {
        when(query.getSingleResult()).thenReturn(1L);
        when(operationalStatusService.findActiveUsernamesInSubtree(UnitType.PARISH, unitId)).thenReturn(Set.of("x"));

        UnitStatusAuditService.ToggleContext context = service.begin(UnitType.PARISH, unitId, "Tạm ngưng hoạt động");
        service.finish(context, UnitType.PARISH, unitId, "admin");

        assertTrue(context.disabling());
        verify(query, times(1)).executeUpdate();
        verify(query).setParameter("status", false);
        verify(query).setParameter("reason", "Tạm ngưng hoạt động");
    }

    @Test
    void enablingInactiveUnitDoesNotRequireReason() {
        when(query.getSingleResult()).thenReturn(0L);
        when(operationalStatusService.findActiveUsernamesInSubtree(UnitType.DIOCESE, unitId)).thenReturn(Set.of());

        UnitStatusAuditService.ToggleContext context = service.begin(UnitType.DIOCESE, unitId, null);
        service.finish(context, UnitType.DIOCESE, unitId, "root");

        assertFalse(context.disabling());
        verify(query).setParameter("status", true);
    }
}
