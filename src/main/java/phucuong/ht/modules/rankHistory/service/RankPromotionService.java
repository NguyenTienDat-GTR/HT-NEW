package phucuong.ht.modules.rankHistory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

// Thăng cấp khi kết thúc khóa và hạ cấp khi chứng nhận của khóa bị thu hồi. Gọi trong transaction của người gọi.
@Service
@RequiredArgsConstructor
public class RankPromotionService {

    private final RankHistoryRepository rankHistoryRepository;

    // Thăng huynh trưởng lên newLevel và ghi lịch sử gắn với khóa (oldLevel là cấp để hạ về khi thu hồi).
    public void promote(Leader leader, String newLevel, String courseCode, String actor, OffsetDateTime now) {
        if (newLevel == null || newLevel.equals(leader.getLeaderLevel())) {
            return;
        }
        RankHistory history = newHistory(leader, leader.getLeaderLevel(), newLevel, courseCode,
                "Thăng cấp khi kết thúc khóa " + courseCode, actor, now);
        rankHistoryRepository.save(history);
        leader.setLeaderLevel(newLevel);
        leader.setUpdatedAt(now);
        leader.setUpdatedBy(actor);
    }

    // Hạ cấp huynh trưởng về cấp trước đó đã đạt, trước lần thăng cấp do khóa này sinh ra.
    // Chỉ hạ khi cấp hiện tại đúng là cấp mà khóa này đã trao; nếu sau đó huynh trưởng đã lên cấp khác thì giữ nguyên.
    // Trả về cấp mới nếu có hạ cấp.
    public Optional<String> revertPromotion(Leader leader, String courseCode, String note, String actor, OffsetDateTime now) {
        Optional<RankHistory> granted = rankHistoryRepository.findLatestActivePromotion(leader.getId(), courseCode);
        if (granted.isEmpty()) {
            return Optional.empty();
        }
        RankHistory promotion = granted.get();
        if (!promotion.getNewLevel().equals(leader.getLeaderLevel())) {
            return Optional.empty();
        }
        String previous = promotion.getOldLevel() == null ? "NONE" : promotion.getOldLevel();
        RankHistory demotion = newHistory(leader, leader.getLeaderLevel(), previous, courseCode, note, actor, now);
        demotion.setReverted(Boolean.TRUE);
        rankHistoryRepository.save(demotion);
        promotion.setReverted(Boolean.TRUE);
        promotion.setUpdatedAt(now);
        promotion.setUpdatedBy(actor);
        leader.setLeaderLevel(previous);
        leader.setUpdatedAt(now);
        leader.setUpdatedBy(actor);
        return Optional.of(previous);
    }

    private RankHistory newHistory(Leader leader, String oldLevel, String newLevel, String courseCode, String note, String actor, OffsetDateTime now) {
        RankHistory history = new RankHistory();
        history.setLeader(leader);
        history.setOldLevel(oldLevel);
        history.setNewLevel(newLevel);
        history.setPromotionDate(LocalDate.now());
        history.setNote(note);
        history.setCourseCode(courseCode);
        history.setReverted(Boolean.FALSE);
        history.setStatus(Boolean.TRUE);
        history.setCreatedAt(now);
        history.setUpdatedAt(now);
        history.setCreatedBy(actor);
        history.setUpdatedBy(actor);
        return history;
    }
}
