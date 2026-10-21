package phucuong.ht.modules.executiveBoard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;
import phucuong.ht.modules.executiveBoard.model.response.ExecutiveBoardSeatResponse;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutiveBoardBoardService {

    private final ExecutiveBoardAssignmentRepository assignmentRepository;
    private final PositionRepository positionRepository;

    /**
     * Ban điều hành của một đơn vị: mỗi chức vụ đang hoạt động cùng cấp là một vị trí.
     * Chức vụ bị vô hiệu hóa không xuất hiện; phân công bị vô hiệu hóa hoặc đã kết thúc thì vị trí hiện "chưa phân công".
     */
    public List<ExecutiveBoardSeatResponse> findBoard(UnitType unitType, UUID unitId) {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
        request.setStatus(Boolean.TRUE);
        request.setFilters(new LinkedHashMap<>(Map.of("positionType", unitType)));
        List<Position> positions = positionRepository.findAll(request).getContent().stream()
                .sorted(Comparator.comparing(Position::getPositionName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // Đã sắp mới bắt đầu nhất trước nên giữ phân công đầu tiên của mỗi chức vụ.
        Map<String, ExecutiveBoardAssignment> byPosition = new LinkedHashMap<>();
        for (ExecutiveBoardAssignment assignment : assignmentRepository.findCurrentByUnit(unitType, unitId)) {
            byPosition.putIfAbsent(assignment.getPosition().getPositionCode(), assignment);
        }

        return positions.stream().map(position -> toSeat(position, byPosition.get(position.getPositionCode()))).toList();
    }

    private ExecutiveBoardSeatResponse toSeat(Position position, ExecutiveBoardAssignment assignment) {
        ExecutiveBoardSeatResponse.ExecutiveBoardSeatResponseBuilder seat = ExecutiveBoardSeatResponse.builder()
                .positionCode(position.getPositionCode())
                .positionName(position.getPositionName())
                .assigned(assignment != null);
        if (assignment == null) {
            return seat.build();
        }
        Leader leader = assignment.getLeader();
        return seat
                .assignmentId(assignment.getId())
                .leaderId(leader.getId())
                .leaderHolyName(leader.getHolyName())
                .leaderFullName(leader.getLastName() == null || leader.getLastName().isBlank()
                        ? leader.getFirstName() : leader.getLastName() + " " + leader.getFirstName())
                .leaderEmail(leader.getEmail())
                .leaderPhoneNumber(leader.getPhoneNumber())
                .leaderImageUrl(leader.getImageUrl())
                .leaderParishName(leader.getParish() == null ? null : leader.getParish().getName())
                .leaderDeaneryName(leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getName())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .build();
    }
}
