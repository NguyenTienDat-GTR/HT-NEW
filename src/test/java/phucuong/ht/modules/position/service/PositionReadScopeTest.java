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
    private static final UUID PARISH_A = UUID.randomUUID();

    @Test
    void parishReaderOnlyReceivesParishPositionsBeforePagination() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("parish-admin")).thenReturn(Optional.of(PHU_CUONG));
        Position diocese = position("DIO", UnitType.DIOCESE);
        Position parish = position("PAR", UnitType.PARISH);
        parish.setUnitId(PARISH_A);
        lenient().when(accountRepository.findActiveParishIdByUsername("parish-admin")).thenReturn(Optional.of(PARISH_A));
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
        position.setUnitId(PHU_CUONG);
        return position;
    }

    private Position positionOf(String code, UnitType type, UUID dioceseId) {
        Position position = position(code, type);
        position.setDioceseId(dioceseId);
        position.setUnitId(dioceseId);
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

    private static final UUID DEANERY_A = UUID.randomUUID();
    private static final UUID DEANERY_B = UUID.randomUUID();

    private Position deaneryPosition(String code, UUID deaneryId) {
        Position position = position(code, UnitType.DEANERY);
        position.setUnitId(deaneryId);
        return position;
    }

    @Test
    void deaneryAdminOnlySeesPositionsOfItsOwnDeanery() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("de-a")).thenReturn(Optional.of(PHU_CUONG));
        lenient().when(accountRepository.findActiveDeaneryIdByUsername("de-a")).thenReturn(Optional.of(DEANERY_A));
        lenient().when(accountRepository.findDirectActiveDeaneryIdByUsername("de-a")).thenReturn(Optional.of(DEANERY_A));
        when(positionRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(
                List.of(deaneryPosition("A-1", DEANERY_A), deaneryPosition("B-1", DEANERY_B)),
                PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));

        var response = service().findAll(new BaseSearchRequest(), admin("de-a", "organization.position.read.deanery"));

        assertEquals(1, response.getTotalElements());
        assertEquals("A-1", response.getContent().getFirst().getPositionCode());
        assertEquals(DEANERY_A, response.getContent().getFirst().getUnitId());
    }

    @Test
    void dioceseAdminSeesPositionsOfEveryDeaneryInItsDiocese() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("pc-admin")).thenReturn(Optional.of(PHU_CUONG));
        lenient().when(accountRepository.findDirectActiveDioceseIdByUsername("pc-admin")).thenReturn(Optional.of(PHU_CUONG));
        when(positionRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(
                List.of(deaneryPosition("A-1", DEANERY_A), deaneryPosition("B-1", DEANERY_B)),
                PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));

        var response = service().findAll(new BaseSearchRequest(), admin("pc-admin", "organization.position.read.deanery"));

        assertEquals(2, response.getTotalElements());
    }

    @Test
    void createStoresThePositionInTheCallersDeanery() {
        when(accountRepository.findActiveDioceseIdByUsername("de-a")).thenReturn(Optional.of(PHU_CUONG));
        when(accountRepository.findDirectActiveDeaneryIdByUsername("de-a")).thenReturn(Optional.of(DEANERY_A));
        when(positionRepository.nextDisplayOrder(UnitType.DEANERY, DEANERY_A)).thenReturn(1);
        PositionCreateRequest request = new PositionCreateRequest();
        request.setPositionName("Thư ký");
        request.setPositionType(UnitType.DEANERY);

        var response = service().create(request, admin("de-a", "organization.position.create.deanery"));

        assertEquals(DEANERY_A, response.getUnitId());
        verify(positionRepository).save(org.mockito.ArgumentMatchers.argThat(
                saved -> DEANERY_A.equals(saved.getUnitId()) && PHU_CUONG.equals(saved.getDioceseId())));
    }

    @Test
    void dioceseAdminCannotCreateDeaneryPositionsBecauseItHasNoDeanery() {
        when(accountRepository.findActiveDioceseIdByUsername("pc-admin")).thenReturn(Optional.of(PHU_CUONG));
        PositionCreateRequest request = new PositionCreateRequest();
        request.setPositionName("Thư ký");
        request.setPositionType(UnitType.DEANERY);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service().create(request, admin("pc-admin", "organization.position.create.deanery")));
    }

    @Test
    void positionTypeCannotBeChangedAfterCreation() {
        lenient().when(accountRepository.findActiveDioceseIdByUsername("de-a")).thenReturn(Optional.of(PHU_CUONG));
        lenient().when(accountRepository.findActiveDeaneryIdByUsername("de-a")).thenReturn(Optional.of(DEANERY_A));
        when(positionRepository.findById("A-1")).thenReturn(Optional.of(deaneryPosition("A-1", DEANERY_A)));
        var update = new phucuong.ht.modules.position.model.request.PositionUpdateRequest();
        update.setPositionType(UnitType.PARISH);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service().update("A-1", update, admin("de-a", "organization.position.update.deanery")));
    }
}
