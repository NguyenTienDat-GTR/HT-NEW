package phucuong.ht.modules.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.modules.auth.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisTokenSessionService {

    private static final String SESSION_KEY_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "auth:user:";
    private static final int MAX_ACTIVE_IPS = 2;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    public TokenSessionState createSession(
            String username,
            String ipAddress,
            String accessTokenJti,
            String refreshTokenJti,
            String refreshToken
    ) {
        cleanupUserSessions(username);
        revokeSessionsByIp(username, ipAddress);

        List<TokenSessionState> activeSessions = listActiveSessions(username);
        while (activeSessions.size() >= MAX_ACTIVE_IPS) {
            TokenSessionState oldestSession = activeSessions.stream()
                    .min(Comparator.comparing(this::evictionInstant))
                    .orElseThrow(() -> invalidSession("Không thể xác định phiên đăng nhập cần thu hồi"));
            revokeSession(oldestSession.getSessionId());
            activeSessions.removeIf(session -> Objects.equals(session.getSessionId(), oldestSession.getSessionId()));
        }

        Instant now = Instant.now();
        TokenSessionState sessionState = new TokenSessionState(
                UUID.randomUUID().toString(),
                username,
                ipAddress,
                accessTokenJti,
                refreshTokenJti,
                hashRefreshToken(refreshToken),
                now,
                now.plusMillis(authProperties.getJwt().getRefreshTokenExpirationMs()),
                now
        );
        saveSession(sessionState);
        return sessionState;
    }

    public TokenSessionState requireRefreshSession(
            String sessionId,
            String username,
            String refreshTokenJti,
            String refreshToken
    ) {
        TokenSessionState sessionState = requireSession(sessionId);
        if (!Objects.equals(sessionState.getUsername(), username)
                || !Objects.equals(sessionState.getRefreshTokenJti(), refreshTokenJti)
                || !Objects.equals(sessionState.getRefreshTokenHash(), hashRefreshToken(refreshToken))) {
            revokeSession(sessionId);
            throw invalidSession("Phiên đăng nhập không còn hiệu lực");
        }
        touchSession(sessionState, Instant.now());
        return sessionState;
    }

    public TokenSessionState rotateSession(
            String sessionId,
            String username,
            String accessTokenJti,
            String refreshTokenJti,
            String refreshToken
    ) {
        TokenSessionState sessionState = requireSession(sessionId);
        if (!Objects.equals(sessionState.getUsername(), username)) {
            revokeSession(sessionId);
            throw invalidSession("Phiên đăng nhập không còn hiệu lực");
        }

        Instant now = Instant.now();
        sessionState.setAccessTokenJti(accessTokenJti);
        sessionState.setRefreshTokenJti(refreshTokenJti);
        sessionState.setRefreshTokenHash(hashRefreshToken(refreshToken));
        sessionState.setExpiresAt(now.plusMillis(authProperties.getJwt().getRefreshTokenExpirationMs()));
        sessionState.setLastUsedAt(now);
        saveSession(sessionState);
        return sessionState;
    }

    public boolean isAccessTokenActive(String sessionId, String username, String accessTokenJti) {
        TokenSessionState sessionState = getSession(sessionId);
        if (sessionState == null) {
            return false;
        }
        if (isExpired(sessionState)) {
            revokeSession(sessionId);
            return false;
        }
        if (!Objects.equals(sessionState.getUsername(), username)
                || !Objects.equals(sessionState.getAccessTokenJti(), accessTokenJti)) {
            return false;
        }

        touchSession(sessionState, Instant.now());
        return true;
    }

    public void revokeSession(String sessionId) {
        TokenSessionState sessionState = getSession(sessionId);
        deleteSessionKeys(sessionId, sessionState == null ? null : sessionState.getUsername());
    }

    public List<TokenSessionState> listActiveSessions(String username) {
        return cleanupUserSessions(username);
    }

    private List<TokenSessionState> cleanupUserSessions(String username) {
        String userSessionsKey = userSessionsKey(username);
        Set<String> sessionIds = stringRedisTemplate.opsForZSet().range(userSessionsKey, 0, -1);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }

        List<TokenSessionState> activeSessions = new ArrayList<>();
        for (String sessionId : sessionIds) {
            TokenSessionState sessionState = getSession(sessionId);
            if (sessionState == null || isExpired(sessionState)) {
                deleteSessionKeys(sessionId, username);
                continue;
            }
            activeSessions.add(sessionState);
        }
        return activeSessions;
    }

    private void revokeSessionsByIp(String username, String ipAddress) {
        for (TokenSessionState sessionState : listActiveSessions(username)) {
            if (Objects.equals(sessionState.getIpAddress(), ipAddress)) {
                revokeSession(sessionState.getSessionId());
            }
        }
    }

    private TokenSessionState requireSession(String sessionId) {
        TokenSessionState sessionState = getSession(sessionId);
        if (sessionState == null || isExpired(sessionState)) {
            revokeSession(sessionId);
            throw invalidSession("Phiên đăng nhập không còn hiệu lực");
        }
        return sessionState;
    }

    private TokenSessionState getSession(String sessionId) {
        String rawSession = readValue(sessionKey(sessionId));
        if (rawSession == null || rawSession.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(rawSession, TokenSessionState.class);
        } catch (JsonProcessingException exception) {
            deleteSessionKeys(sessionId, null);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Không thể đọc dữ liệu phiên đăng nhập");
        }
    }

    private void saveSession(TokenSessionState sessionState) {
        try {
            Duration ttl = resolveTtl(sessionState.getExpiresAt());
            stringRedisTemplate.opsForValue().set(
                    sessionKey(sessionState.getSessionId()),
                    objectMapper.writeValueAsString(sessionState),
                    ttl
            );
            stringRedisTemplate.opsForZSet().add(
                    userSessionsKey(sessionState.getUsername()),
                    sessionState.getSessionId(),
                    score(sessionState.getLastUsedAt())
            );
            stringRedisTemplate.expire(userSessionsKey(sessionState.getUsername()), ttl);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Không thể lưu dữ liệu phiên đăng nhập");
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Hệ thống phiên đăng nhập tạm thời không khả dụng");
        }
    }

    private void touchSession(TokenSessionState sessionState, Instant touchedAt) {
        sessionState.setLastUsedAt(touchedAt);
        saveSession(sessionState);
    }

    private boolean isExpired(TokenSessionState sessionState) {
        return sessionState.getExpiresAt() == null || !sessionState.getExpiresAt().isAfter(Instant.now());
    }

    private Duration resolveTtl(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private Instant evictionInstant(TokenSessionState sessionState) {
        return sessionState.getLastUsedAt() != null ? sessionState.getLastUsedAt() : sessionState.getIssuedAt();
    }

    private double score(Instant instant) {
        return instant == null ? Instant.now().toEpochMilli() : instant.toEpochMilli();
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Không thể mã hóa refresh token");
        }
    }

    private String readValue(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Hệ thống phiên đăng nhập tạm thời không khả dụng");
        }
    }

    private void deleteSessionKeys(String sessionId, String username) {
        try {
            stringRedisTemplate.delete(sessionKey(sessionId));
            if (username != null && !username.isBlank()) {
                stringRedisTemplate.opsForZSet().remove(userSessionsKey(username), sessionId);
            }
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Hệ thống phiên đăng nhập tạm thời không khả dụng");
        }
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String userSessionsKey(String username) {
        return USER_SESSIONS_KEY_PREFIX + username + ":sessions";
    }

    private ApiException invalidSession(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, errorCode.INVALID_TOKEN, message);
    }
}
