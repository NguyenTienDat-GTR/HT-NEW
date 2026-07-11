package phucuong.ht.modules.executiveBoard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentCreateRequest;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveBoardAssignmentServiceTest {

    @Mock
    private ExecutiveBoardAssignmentRepository assignmentRepository;
    @Mock
    private LeaderRepository leaderRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private DioceseRepository dioceseRepository;
    @Mock
    private DeaneryRepository deaneryRepository;
    @Mock
    private ParishRepository parishRepository;
    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void writeOperationsShouldRunInASingleTransaction() throws NoSuchMethodException {
        assertTrue(ExecutiveBoardAssignmentService.class
                .getMethod("create", ExecutiveBoardAssignmentCreateRequest.class, org.springframework.security.core.Authentication.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(ExecutiveBoardAssignmentService.class
                .getMethod("update", UUID.class, phucuong.ht.modules.executiveBoard.model.request.ExecutiveBoardAssignmentUpdateRequest.class, org.springframework.security.core.Authentication.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(ExecutiveBoardAssignmentService.class
                .getMethod("toggleStatus", UUID.class, phucuong.ht.common.model.request.StatusToggleRequest.class, org.springframework.security.core.Authentication.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void createShouldRejectDuplicateActiveAssignmentInSameUnit() {
        CommonValidationHelper commonValidationHelper = new CommonValidationHelper(new securityUtils());
        ExecutiveBoardAssignmentService service = new ExecutiveBoardAssignmentService(
                assignmentRepository,
                leaderRepository,
                positionRepository,
                dioceseRepository,
                deaneryRepository,
                parishRepository,
                accountScopeValidationHelper,
                commonValidationHelper,
                new securityUtils()
        );

        UUID deaneryId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        ExecutiveBoardAssignmentCreateRequest request = new ExecutiveBoardAssignmentCreateRequest();
        request.setUnitType(UnitType.DEANERY);
        request.setUnitId(deaneryId);
        request.setLeaderId(leaderId);
        request.setPositionCode("POS-1");

        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setDiocese(diocese);

        Leader leader = new Leader();
        leader.setId(leaderId);
        var parish = new phucuong.ht.modules.parish.model.entity.Parish();
        parish.setId(UUID.randomUUID());
        parish.setDeanery(deanery);
        leader.setParish(parish);

        Position position = new Position();
        position.setPositionCode("POS-1");
        position.setPositionType(UnitType.DEANERY);

        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));
        when(positionRepository.findById("POS-1")).thenReturn(Optional.of(position));
        when(deaneryRepository.findByIdWithDiocese(deaneryId)).thenReturn(Optional.of(deanery));
        when(accountScopeValidationHelper.requireActiveDeaneryId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(deaneryId);
        when(assignmentRepository.existsActiveCurrentAssignment(UnitType.DEANERY, deaneryId, "POS-1", null)).thenReturn(true);

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-deanery",
                leaderId.toString(),
                List.of("ADMIN_DEANERY"),
                List.of("organization.executive_board_assignment.create.deanery")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        var exception = assertThrows(Exception.class, () -> service.create(request, authentication));
        assertEquals("Mỗi chức vụ chỉ được gán cho một leader trong cùng đơn vị", exception.getMessage());
    }
}
