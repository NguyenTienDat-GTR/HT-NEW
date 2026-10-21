package phucuong.ht.modules.executiveBoard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveBoardBoardServiceTest {

    @Mock
    private ExecutiveBoardAssignmentRepository assignmentRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private phucuong.ht.modules.diocese.repository.DioceseRepository dioceseRepository;
    @Mock
    private phucuong.ht.modules.deanery.repository.DeaneryRepository deaneryRepository;
    @Mock
    private phucuong.ht.modules.parish.repository.ParishRepository parishRepository;

    @Test
    void positionWithoutCurrentAssignmentIsShownAsUnassigned() {
        UUID unitId = UUID.randomUUID();
        Position president = position("P1", "Trưởng ban");
        Position secretary = position("P2", "Thư ký");
        when(positionRepository.findAll(any(BaseSearchRequest.class))).thenReturn(new PageImpl<>(List.of(secretary, president)));

        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setFirstName("An");
        leader.setLastName("Nguyễn");
        ExecutiveBoardAssignment assignment = new ExecutiveBoardAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPosition(president);
        assignment.setLeader(leader);
        when(assignmentRepository.findCurrentByUnit(UnitType.DEANERY, unitId)).thenReturn(List.of(assignment));

        var seats = new ExecutiveBoardBoardService(assignmentRepository, positionRepository, dioceseRepository, deaneryRepository, parishRepository).findBoard(UnitType.DEANERY, unitId);

        assertEquals(3, seats.size());
        assertTrue(seats.get(0).isChaplain());
        // Sau tuyên úy (thứ tự 0), chức vụ xếp theo thứ tự hiển thị: Trưởng ban (1) rồi Thư ký (2).
        assertEquals("Trưởng ban", seats.get(1).getPositionName());
        assertTrue(seats.get(1).isAssigned());
        assertEquals("Nguyễn An", seats.get(1).getLeaderFullName());
        assertFalse(seats.get(2).isAssigned());
    }

    private Position position(String code, String name) {
        Position position = new Position();
        position.setPositionCode(code);
        position.setPositionName(name);
        position.setPositionType(UnitType.DEANERY);
        position.setStatus(Boolean.TRUE);
        position.setDisplayOrder(code.equals("P1") ? 1 : 2);
        return position;
    }
}
