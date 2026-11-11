package phucuong.ht.modules.rankHistory.repository;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RankHistoryAchievedYearTest {

    private static final LocalDate PROMOTION = LocalDate.of(2026, 3, 1);

    @Test
    void certificateApprovalYearWinsWhenCourseIssuesCertificate() {
        OffsetDateTime approved = OffsetDateTime.of(2025, 12, 30, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(2025, RankHistoryRepository.achievedYear(PROMOTION, approved, 2024, LocalDate.of(2024, 8, 1)));
    }

    @Test
    void hostYearIsUsedWhenCourseHasNoCertificate() {
        assertEquals(2024, RankHistoryRepository.achievedYear(PROMOTION, null, 2024, LocalDate.of(2025, 1, 1)));
    }

    @Test
    void courseEndYearIsUsedWhenHostYearMissing() {
        assertEquals(2025, RankHistoryRepository.achievedYear(PROMOTION, null, null, LocalDate.of(2025, 1, 1)));
    }

    @Test
    void promotionYearIsUsedWhenHistoryHasNoCourse() {
        assertEquals(2026, RankHistoryRepository.achievedYear(PROMOTION, null, null, null));
        assertNull(RankHistoryRepository.achievedYear(null, null, null, null));
    }
}
