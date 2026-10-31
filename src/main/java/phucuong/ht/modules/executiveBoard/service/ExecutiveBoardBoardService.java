package phucuong.ht.modules.executiveBoard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;
import phucuong.ht.modules.executiveBoard.model.response.ExecutiveBoardSeatResponse;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.service.MediaUrls;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutiveBoardBoardService {

    public static final String CHAPLAIN_CODE = "CHAPLAIN";

    private final ExecutiveBoardAssignmentRepository assignmentRepository;
    private final PositionRepository positionRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final MediaUrls mediaUrls;

    /**
     * Ban điều hành của một đơn vị: mỗi chức vụ đang hoạt động cùng cấp là một vị trí.
     * Chức vụ bị vô hiệu hóa không xuất hiện; phân công bị vô hiệu hóa hoặc đã kết thúc thì vị trí hiện "chưa phân công".
     */
    public List<ExecutiveBoardSeatResponse> findBoard(UnitType unitType, UUID unitId) {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
        request.setStatus(Boolean.TRUE);
        LinkedHashMap<String, Object> filters = new LinkedHashMap<>(Map.of("positionType", unitType));
        // Mỗi đơn vị có danh sách chức vụ riêng: chỉ hiện chức vụ do chính đơn vị này tạo.
        filters.put("unitId", unitId);
        request.setFilters(filters);
        List<Position> positions = positionRepository.findAll(request).getContent().stream()
                .sorted(Comparator.comparing(Position::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Position::getPositionName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // Đã sắp mới bắt đầu nhất trước nên giữ phân công đầu tiên của mỗi chức vụ.
        // Hiển thị phân công đang hiệu lực hôm nay (đã tới ngày bắt đầu); occupied gồm cả nhiệm kì đặt trước.
        LocalDate today = LocalDate.now();
        Map<String, ExecutiveBoardAssignment> byPosition = new LinkedHashMap<>();
        Set<String> occupied = new HashSet<>();
        for (ExecutiveBoardAssignment assignment : assignmentRepository.findCurrentByUnit(unitType, unitId)) {
            String code = assignment.getPosition().getPositionCode();
            occupied.add(code);
            if (assignment.getStartDate() == null || !assignment.getStartDate().isAfter(today)) {
                byPosition.putIfAbsent(code, assignment);
            }
        }

        List<ExecutiveBoardSeatResponse> seats = new java.util.ArrayList<>();
        seats.add(chaplainSeat(unitType, unitId));
        positions.forEach(position -> seats.add(toSeat(position, byPosition.get(position.getPositionCode()), occupied.contains(position.getPositionCode()))));
        return seats;
    }

    private ExecutiveBoardSeatResponse chaplainSeat(UnitType unitType, UUID unitId) {
        String chaplain = switch (unitType) {
            case DIOCESE -> dioceseRepository.findById(unitId).map(unit -> unit.getChaplain()).orElse(null);
            case DEANERY -> deaneryRepository.findById(unitId).map(unit -> unit.getChaplain()).orElse(null);
            case PARISH -> parishRepository.findById(unitId).map(unit -> unit.getChaplain()).orElse(null);
        };
        boolean assigned = chaplain != null && !chaplain.isBlank();
        return ExecutiveBoardSeatResponse.builder()
                .positionCode(CHAPLAIN_CODE)
                .positionName("Linh mục tuyên úy")
                .displayOrder(0)
                .chaplain(true)
                .assigned(assigned)
                .occupied(assigned)
                .leaderFullName(assigned ? chaplain.trim() : null)
                .build();
    }

    private ExecutiveBoardSeatResponse toSeat(Position position, ExecutiveBoardAssignment assignment, boolean occupied) {
        ExecutiveBoardSeatResponse.ExecutiveBoardSeatResponseBuilder seat = ExecutiveBoardSeatResponse.builder()
                .positionCode(position.getPositionCode())
                .positionName(position.getPositionName())
                .displayOrder(position.getDisplayOrder())
                .assigned(assignment != null)
                .occupied(occupied);
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
                .leaderImageUrl(mediaUrls.smallOrLegacy(leader.getImageAssetId(), leader.getLegacyImageData(), MediaCategory.LEADER_AVATAR))
                .leaderParishName(leader.getParish() == null ? null : leader.getParish().getName())
                .leaderDeaneryName(leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getName())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .build();
    }
}
