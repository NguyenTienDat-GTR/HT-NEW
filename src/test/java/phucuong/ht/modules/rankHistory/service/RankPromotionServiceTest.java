package phucuong.ht.modules.rankHistory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankPromotionServiceTest {

    private RankHistoryRepository repository;
    private RankPromotionService service;
    private Leader leader;
    private final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        repository = mock(RankHistoryRepository.class);
        service = new RankPromotionService(repository);
        leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setLeaderLevel("DU_TRUONG");
    }

    private RankHistory promotion(String oldLevel, String newLevel) {
        RankHistory history = new RankHistory();
        history.setLeader(leader);
        history.setOldLevel(oldLevel);
        history.setNewLevel(newLevel);
        history.setCourseCode("K1");
        history.setReverted(false);
        return history;
    }

    @Test
    void promoteRecordsHistoryLinkedToTheCourse() {
        service.promote(leader, "HT_I", "K1", "admin", now);

        assertEquals("HT_I", leader.getLeaderLevel());
        ArgumentCaptor<RankHistory> captor = ArgumentCaptor.forClass(RankHistory.class);
        verify(repository).save(captor.capture());
        assertEquals("DU_TRUONG", captor.getValue().getOldLevel());
        assertEquals("HT_I", captor.getValue().getNewLevel());
        assertEquals("K1", captor.getValue().getCourseCode());
    }

    @Test
    void revertGoesBackToTheLevelBeforeThePromotion() {
        leader.setLeaderLevel("HT_I");
        RankHistory granted = promotion("DU_TRUONG", "HT_I");
        when(repository.findLatestActivePromotion(leader.getId(), "K1")).thenReturn(Optional.of(granted));

        Optional<String> result = service.revertPromotion(leader, "K1", "Hạ cấp do thu hồi", "admin", now);

        assertEquals(Optional.of("DU_TRUONG"), result);
        assertEquals("DU_TRUONG", leader.getLeaderLevel());
        assertTrue(granted.getReverted());
        ArgumentCaptor<RankHistory> captor = ArgumentCaptor.forClass(RankHistory.class);
        verify(repository).save(captor.capture());
        assertEquals("HT_I", captor.getValue().getOldLevel());
        assertEquals("DU_TRUONG", captor.getValue().getNewLevel());
    }

    @Test
    void revertSkipsWhenLeaderAlreadyMovedToAnotherLevel() {
        // Đã lên HT cấp II nhờ khóa khác: thu hồi chứng nhận của khóa trước không hạ cấp.
        leader.setLeaderLevel("HT_II");
        RankHistory granted = promotion("DU_TRUONG", "HT_I");
        when(repository.findLatestActivePromotion(leader.getId(), "K1")).thenReturn(Optional.of(granted));

        assertEquals(Optional.empty(), service.revertPromotion(leader, "K1", "x", "admin", now));
        assertEquals("HT_II", leader.getLeaderLevel());
        assertFalse(granted.getReverted());
        verify(repository, never()).save(any());
    }

    @Test
    void revertDoesNothingWhenCourseNeverPromoted() {
        when(repository.findLatestActivePromotion(leader.getId(), "K1")).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.revertPromotion(leader, "K1", "x", "admin", now));
        assertEquals("DU_TRUONG", leader.getLeaderLevel());
    }
}
