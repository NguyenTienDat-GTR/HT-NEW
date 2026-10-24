package phucuong.ht.modules.training.courseTeam.model.response;

import java.util.List;

/**
 * Tổng quan chia đội của khóa. unitType = DEANERY (khóa giáo phận) hoặc PARISH (khóa giáo hạt);
 * activeUnitCount là số đơn vị cùng loại đang hoạt động trong phạm vi khóa (giới hạn trên của "tối đa N đơn vị mỗi đội").
 */
public record TeamOverviewResponse(String courseCode, int totalParticipants, int male, int female, int other,
                                   int dividedParticipants, String unitType, int activeUnitCount, List<TeamSummary> teams) {
}
