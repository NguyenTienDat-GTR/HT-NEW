package phucuong.ht.modules.auth.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TokenSessionState {

    private String sessionId;
    private String username;
    private String ipAddress;
    private String accessTokenJti;
    private List<String> previousAccessTokenJtis = new ArrayList<>();
    private String refreshTokenJti;
    private String refreshTokenHash;
    /** Hash refresh token vừa bị thay kèm thời điểm (dạng "hash@epochMillis"), còn dùng được trong thời gian ân hạn ngắn. */
    private List<String> previousRefreshTokens = new ArrayList<>();
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant lastUsedAt;
    /** null = phiên tạo trước khi có ghi nhớ đăng nhập: coi như ghi nhớ, hạn tuyệt đối tính từ issuedAt. */
    private Boolean rememberMe;
    private Instant absoluteExpiresAt;

    public TokenSessionState(String sessionId, String username, String ipAddress, String accessTokenJti,
                             List<String> previousAccessTokenJtis, String refreshTokenJti, String refreshTokenHash,
                             Instant issuedAt, Instant expiresAt, Instant lastUsedAt, Boolean rememberMe, Instant absoluteExpiresAt) {
        this.sessionId = sessionId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.accessTokenJti = accessTokenJti;
        this.previousAccessTokenJtis = previousAccessTokenJtis;
        this.refreshTokenJti = refreshTokenJti;
        this.refreshTokenHash = refreshTokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
        this.rememberMe = rememberMe;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }
}
