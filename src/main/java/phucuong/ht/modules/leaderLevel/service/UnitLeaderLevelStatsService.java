package phucuong.ht.modules.leaderLevel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.dto.UnitLeaderLevelStatsResponse;
import phucuong.ht.modules.leaderLevel.repository.LeaderLevelRepository;
import phucuong.ht.modules.leaderLevel.repository.LeaderLevelRepository.StatsScope;

import java.util.List;
import java.util.UUID;

// Thống kê số huynh trưởng theo cấp độ cho trang chi tiết giáo phận / giáo hạt / giáo xứ.
// Quyền xem do controller của từng đơn vị kiểm tra (cùng quyền xem chi tiết đơn vị).
@Service
@RequiredArgsConstructor
public class UnitLeaderLevelStatsService {

    private final LeaderLevelRepository leaderLevelRepository;

    public UnitLeaderLevelStatsResponse forDiocese(UUID dioceseId) {
        return stats(StatsScope.DIOCESE, dioceseId);
    }

    public UnitLeaderLevelStatsResponse forDeanery(UUID deaneryId) {
        return stats(StatsScope.DEANERY, deaneryId);
    }

    public UnitLeaderLevelStatsResponse forParish(UUID parishId) {
        return stats(StatsScope.PARISH, parishId);
    }

    private UnitLeaderLevelStatsResponse stats(StatsScope scope, UUID unitId) {
        List<UnitLeaderLevelStatsResponse.Item> levels = leaderLevelRepository.countLeadersByLevel(scope, unitId).stream()
                .map(row -> new UnitLeaderLevelStatsResponse.Item(
                        (String) row[0], (String) row[1], ((Number) row[2]).intValue(), ((Number) row[3]).longValue()))
                .toList();
        return new UnitLeaderLevelStatsResponse(levels.stream().mapToLong(UnitLeaderLevelStatsResponse.Item::leaderCount).sum(), levels);
    }
}
