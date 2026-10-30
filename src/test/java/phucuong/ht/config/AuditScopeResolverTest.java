package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.config.audit.AuditScopeSource;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AuditScopeResolverTest {

    private final UUID dioceseId = UUID.randomUUID();

    private AuditScopeResolver resolver() {
        AuditScopeSource diocese = AuditScopeSource.of("diocese", id -> {
            UUID parsed = UUID.fromString(id);
            return AuditScopeContext.builder().unitType(UnitType.DIOCESE).unitId(parsed).dioceseId(parsed).build();
        });
        return new AuditScopeResolver(mock(securityUtils.class), mock(AccountRepository.class), List.of(diocese));
    }

    @Test
    void delegatesToTheSourceRegisteredForTheResource() {
        AuditScopeContext scope = resolver().resolveFromResource("diocese", dioceseId.toString());

        assertEquals(UnitType.DIOCESE, scope.getUnitType());
        assertEquals(dioceseId, scope.getDioceseId());
    }

    @Test
    void unknownResourceBlankIdAndInvalidIdYieldAnEmptyScope() {
        AuditScopeResolver resolver = resolver();

        assertNull(resolver.resolveFromResource("unknown", dioceseId.toString()).getUnitType());
        assertNull(resolver.resolveFromResource("diocese", " ").getUnitType());
        assertNull(resolver.resolveFromResource(null, dioceseId.toString()).getUnitType());
        assertNull(resolver.resolveFromResource("diocese", "not-a-uuid").getUnitType());
    }
}
