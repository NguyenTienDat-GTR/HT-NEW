package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.rankHistory.model.response.RankHistoryResponse;

import java.util.List;

@Getter
@Builder
public class MyProfileResponse {

    private final String username;
    /** Vai trò đang hiệu lực, vai trò chính luôn đứng đầu. */
    private final List<RoleItem> roles;
    /** Email nhận OTP; null nếu tài khoản chưa có email. */
    private final String email;
    private final boolean hasLeader;
    /** Tài khoản có quyền sửa ngày sinh trong hồ sơ của chính mình (quyền organization.leader.update_birth_date.* đúng phạm vi); false nếu không gắn huynh trưởng. */
    private final boolean canEditBirthDate;
    /** Ảnh đại diện của tài khoản không gắn huynh trưởng (URL đã ký); null nếu chưa có hoặc tài khoản gắn huynh trưởng. */
    private final String imageUrl;
    private final String imageThumbUrl;
    private final java.util.UUID imageAssetId;
    /** Hồ sơ Huynh Trưởng gắn với tài khoản; null với tài khoản quản trị không gắn Huynh Trưởng. */
    private final LeaderResponse leader;
    private final List<RankHistoryResponse> rankHistory;
    private final List<CertificateResponse> certificates;

    @Getter
    @Builder
    public static class RoleItem {
        private final String code;
        private final String name;
        private final boolean primary;
    }
}
