package phucuong.ht.modules.auth.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenSessionState {

    private String sessionId;
    private String username;
    private String ipAddress;
    private String accessTokenJti;
    private List<String> previousAccessTokenJtis = new ArrayList<>();
    private String refreshTokenJti;
    private String refreshTokenHash;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant lastUsedAt;
}
