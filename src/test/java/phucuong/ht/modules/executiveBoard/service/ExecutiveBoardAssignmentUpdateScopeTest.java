package phucuong.ht.modules.executiveBoard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;
import phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentUpdateRequest;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveBoardAssignmentUpdateScopeTest {

    @Mock private ExecutiveBoardAssignmentRepository assignmentRepository;
    @Mock private LeaderRepository leaderRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private DioceseRepository dioceseRepository;
    @Mock private DeaneryRepository deaneryRepository;
    @Mock private ParishRepository parishRepository;
    @Mock private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void deaneryUpdaterCannotMoveAnAssignmentToDioceseScopeWithoutDioceseUpdateAuthority() {
        UUID assignmentId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        ExecutiveBoardAssignment existing = new ExecutiveBoardAssignment();
        existing.setId(assignmentId);

        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        Deanery leaderDeanery = new Deanery();
        leaderDeanery.setId(UUID.randomUUID());
        leaderDeanery.setDiocese(diocese);
        var leaderParish = new phucuong.ht.modules.parish.model.entity.Parish();
        leaderParish.setId(UUID.randomUUID());
        leaderParish.setDeanery(leaderDeanery);
        Leader leader = new Leader();
        leader.setId(leaderId);
        leader.setParish(leaderParish);
        Position position = new Position();
        position.setPositionCode("DIO-POS");
        position.setPositionType(UnitType.DIOCESE);

        ExecutiveBoardAssignmentUpdateRequest request = new ExecutiveBoardAssignmentUpdateRequest();
        request.setUnitType(UnitType.DIOCESE);
        request.setUnitId(dioceseId);
        request.setLeaderId(leaderId);
        request.setPositionCode("DIO-POS");

        when(assignmentRepository.findByIdWithRelations(assignmentId)).thenReturn(Optional.of(existing));
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(positionRepository.findById("DIO-POS")).thenReturn(Optional.of(position));
        when(dioceseRepository.findById(dioceseId)).thenReturn(Optional.of(diocese));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-deanery",
                null,
                List.of("ADMIN_DEANERY"),
                List.of("organization.executive_board_assignment.update.deanery")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertThrows(RuntimeException.class, () -> service().update(assignmentId, request, authentication));
    }

    private ExecutiveBoardAssignmentService service() {
        return new ExecutiveBoardAssignmentService(
                assignmentRepository,
                leaderRepository,
                positionRepository,
                dioceseRepository,
                deaneryRepository,
                parishRepository,
                accountScopeValidationHelper,
                new CommonValidationHelper(new securityUtils()),
                new securityUtils()
        );
    }
}
