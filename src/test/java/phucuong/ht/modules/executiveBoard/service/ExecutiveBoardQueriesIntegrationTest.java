package phucuong.ht.modules.executiveBoard.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Chạy thật các truy vấn mới (ban điều hành của đơn vị, lọc năm theo khoảng ngày) để bắt lỗi JPQL/Criteria.
@SpringBootTest
class ExecutiveBoardQueriesIntegrationTest {

    @Autowired
    private ExecutiveBoardBoardService boardService;

    @Autowired
    private ExecutiveBoardAssignmentRepository assignmentRepository;

    @MockitoBean
    private RedisTokenSessionService redisTokenSessionService;

    @Test
    void boardAndOverlapYearQueriesExecute() {
        assertNotNull(boardService.findBoard(UnitType.DIOCESE, UUID.randomUUID()));

        BaseSearchRequest request = new BaseSearchRequest();
        request.setFilters(new LinkedHashMap<>(Map.of("startDate+endDate:overlapYear", "2026")));
        assertNotNull(assignmentRepository.findAll(request));
    }
}
