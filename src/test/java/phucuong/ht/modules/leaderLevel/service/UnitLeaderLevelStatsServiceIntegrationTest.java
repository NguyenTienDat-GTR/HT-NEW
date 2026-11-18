package phucuong.ht.modules.leaderLevel.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import phucuong.ht.common.dto.UnitLeaderLevelStatsResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thống kê huynh trưởng theo cấp độ chạy được trên schema thật cho cả ba cấp đơn vị. */
@SpringBootTest
class UnitLeaderLevelStatsServiceIntegrationTest {

    @Autowired
    private UnitLeaderLevelStatsService service;

    @Test
    void shouldRunAgainstTheRealSchemaForEveryUnitLevel() {
        UUID unknown = UUID.randomUUID();

        for (UnitLeaderLevelStatsResponse stats : new UnitLeaderLevelStatsResponse[]{
                service.forDiocese(unknown), service.forDeanery(unknown), service.forParish(unknown)}) {
            assertEquals(0, stats.total());
            assertTrue(stats.levels().isEmpty(), "Đơn vị không tồn tại thì không có danh mục cấp độ nào");
        }
    }
}
