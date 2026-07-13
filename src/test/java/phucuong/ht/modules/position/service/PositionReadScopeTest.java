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
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionReadScopeTest {

    @Mock
    private PositionRepository positionRepository;

    @Test
    void parishReaderOnlyReceivesParishPositionsBeforePagination() {
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

    private PositionService service() {
        return new PositionService(
                positionRepository,
                new CommonValidationHelper(new securityUtils()),
                new securityUtils()
        );
    }

    private Position position(String code, UnitType type) {
        Position position = new Position();
        position.setPositionCode(code);
        position.setPositionType(type);
        return position;
    }
}
