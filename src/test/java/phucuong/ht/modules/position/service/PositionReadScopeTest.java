package phucuong.ht.modules.position.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.model.request.PositionCreateRequest;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionReadScopeTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private AuditActorNameResolver auditActorNameResolver;

    @Mock
    private AccountRepository accountRepository;

    private static final UUID PHU_CUONG = UUID.randomUUID();
    private static final UUID XUAN_LOC = UUID.randomUUID();

    @Test
    void parishReaderOnlyReceivesParishPositionsBeforePagination() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("parish-admin")).thenReturn(Optional.of(PHU_CUONG));
        Position diocese = position("DIO", UnitType.DIOCESE);
        Position parish = position("PAR", UnitType.PARISH);
        when(positionRepository.findAll(any(BaseSearchRequest.class)))
                .thenReturn(new PageImpl<>(List.of(diocese, parish), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "parish-admin",
                null,
                List.of("ADMIN_PARISH"),
                List.of("organization.position.read.parish")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(1);

        var response = service().findAll(request, authentication);

        assertEquals(1, response.getTotalElements());
        assertEquals("PAR", response.getContent().getFirst().getPositionCode());
    }

    @Test
    void detailOutsideReadScopeIsForbidden() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("parish-admin")).thenReturn(Optional.of(PHU_CUONG));
        when(positionRepository.findById("DIO")).thenReturn(java.util.Optional.of(position("DIO", UnitType.DIOCESE)));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "parish-admin", null, List.of("ADMIN_PARISH"), List.of("organization.position.read.parish"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service().findById("DIO", authentication));
    }

    private PositionService service() {
        return new PositionService(
                positionRepository,
                new CommonValidationHelper(new securityUtils()),
                new securityUtils(),
                auditActorNameResolver,
                accountRepository
        );
    }

    private Position position(String code, UnitType type) {
        Position position = new Position();
        position.setPositionCode(code);
        position.setPositionType(type);
        position.setDioceseId(PHU_CUONG);
        return position;
    }

    private Position positionOf(String code, UnitType type, UUID dioceseId) {
        Position position = position(code, type);
        position.setDioceseId(dioceseId);
        return position;
    }

    private static org.springframework.security.core.Authentication admin(String username, String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of("ADMIN_DIOCESE"), List.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void listOnlyContainsPositionsOfTheCallersOwnDiocese() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("pc-admin")).thenReturn(Optional.of(PHU_CUONG));
        when(positionRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(
                List.of(positionOf("PC-1", UnitType.DIOCESE, PHU_CUONG), positionOf("XL-1", UnitType.DIOCESE, XUAN_LOC)),
                PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));

        var response = service().findAll(new BaseSearchRequest(), admin("pc-admin", "organization.position.read.diocese"));

        assertEquals(1, response.getTotalElements());
        assertEquals("PC-1", response.getContent().getFirst().getPositionCode());
        assertEquals(PHU_CUONG, response.getContent().getFirst().getDioceseId());
    }

    @Test
    void accountWithoutDioceseSeesNoPositions() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("nobody")).thenReturn(Optional.empty());
        when(positionRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(
                List.of(positionOf("PC-1", UnitType.DIOCESE, PHU_CUONG)), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 1));

        var response = service().findAll(new BaseSearchRequest(), admin("nobody", "organization.position.read.diocese"));

        assertEquals(0, response.getTotalElements());
    }

    @Test
    void detailOfAnotherDiocesePositionIsForbidden() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("pc-admin")).thenReturn(Optional.of(PHU_CUONG));
        when(positionRepository.findById("XL-1")).thenReturn(Optional.of(positionOf("XL-1", UnitType.DIOCESE, XUAN_LOC)));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service().findById("XL-1", admin("pc-admin", "organization.position.read.diocese")));
    }

    @Test
    void createStoresThePositionInTheCallersDiocese() {
        when(accountRepository.findActiveDioceseIdByUsername("pc-admin")).thenReturn(Optional.of(PHU_CUONG));
        when(positionRepository.nextDisplayOrder(UnitType.DIOCESE, PHU_CUONG)).thenReturn(3);
        PositionCreateRequest request = new PositionCreateRequest();
        request.setPositionName("Phó chủ nhiệm");
        request.setPositionType(UnitType.DIOCESE);

        var response = service().create(request, admin("pc-admin", "organization.position.create.diocese"));

        assertEquals(PHU_CUONG, response.getDioceseId());
        assertEquals(3, response.getDisplayOrder());
        verify(positionRepository).save(org.mockito.ArgumentMatchers.argThat(saved -> PHU_CUONG.equals(saved.getDioceseId())));
    }

    @Test
    void accountWithoutDioceseCannotCreatePositions() {
        when(accountRepository.findActiveDioceseIdByUsername("nobody")).thenReturn(Optional.empty());
        PositionCreateRequest request = new PositionCreateRequest();
        request.setPositionName("Chủ nhiệm");
        request.setPositionType(UnitType.DIOCESE);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service().create(request, admin("nobody", "organization.position.create.diocese")));
    }
}
