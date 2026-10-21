package phucuong.ht.modules.executiveBoard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.modules.executiveBoard.model.response.ExecutiveBoardSeatResponse;
import phucuong.ht.modules.executiveBoard.service.ExecutiveBoardBoardService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/executive-board/boards")
@RequiredArgsConstructor
public class ExecutiveBoardBoardController {

    private final ExecutiveBoardBoardService boardService;

    // Ai xem được chi tiết đơn vị thì xem được ban điều hành của đơn vị đó.
    @GetMapping("/{unitType}/{unitId}")
    @PreAuthorize("(#unitType.name() == 'DIOCESE' and @dioceseAccessEvaluator.canRead(authentication, #unitId))"
            + " or (#unitType.name() == 'DEANERY' and @deaneryAccessEvaluator.canRead(authentication, #unitId))"
            + " or (#unitType.name() == 'PARISH' and @parishAccessEvaluator.canRead(authentication, #unitId))")
    public apiResponse<List<ExecutiveBoardSeatResponse>> findBoard(@PathVariable UnitType unitType, @PathVariable UUID unitId) {
        return apiResponse.success("Lấy ban điều hành thành công", boardService.findBoard(unitType, unitId));
    }
}
