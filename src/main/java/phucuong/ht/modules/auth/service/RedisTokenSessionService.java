package phucuong.ht.modules.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.config.AuthProperties;
import phucuong.ht.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisTokenSessionService {

    private static final String SESSION_KEY_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "auth:user:";
    private static final int MAX_ACTIVE_SESSIONS = 2;
    private static final int MAX_TRACKED_ACCESS_TOKEN_IDS = 4;
    private static final long REFRESH_GRACE_MS = 30_000L;
    private static final DefaultRedisScript<Long> COMPARE_AND_ROTATE_REFRESH_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                      return 0
                    end
                    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                    redis.call('ZADD', KEYS[2], ARGV[5], ARGV[4])
                    redis.call('PEXPIRE', KEYS[2], ARGV[3])
                    return 1
                    """,
            Long.class
    );

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
        return createSession(username, ipAddress, accessTokenJti, refreshTokenJti, refreshToken, true);
    }

    public TokenSessionState createSession(
            String username,
            String ipAddress,
            String accessTokenJti,
            String refreshTokenJti,
            String refreshToken,
            boolean rememberMe
    ) {
        cleanupUserSessions(username);

        List<TokenSessionState> activeSessions = listActiveSessions(username);
        activeSessions.stream()
                .filter(session -> Objects.equals(session.getIpAddress(), ipAddress))
                .map(TokenSessionState::getSessionId)
                .toList()
                .forEach(this::revokeSession);
        activeSessions = listActiveSessions(username);
        while (activeSessions.size() >= MAX_ACTIVE_SESSIONS) {
            TokenSessionState oldestSession = activeSessions.stream()
                    .min(Comparator.comparingDouble(session -> evictionScore(username, session)))
                    .orElseThrow(() -> invalidSession("Khong the xac dinh phien dang nhap can thu hoi"));
            revokeSession(oldestSession.getSessionId());
            activeSessions.removeIf(session -> Objects.equals(session.getSessionId(), oldestSession.getSessionId()));
        }

        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plusMillis(absoluteMs(rememberMe));
        Instant idleExpiresAt = now.plusMillis(idleMs(rememberMe));
        TokenSessionState sessionState = new TokenSessionState(
                UUID.randomUUID().toString(),
                username,
                ipAddress,
                accessTokenJti,
                new ArrayList<>(),
                refreshTokenJti,
                hashRefreshToken(refreshToken),
                now,
                idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt,
                now,
                rememberMe,
                absoluteExpiresAt
        );
        saveSession(sessionState);
        return sessionState;
    }

    /** Phiên còn hiệu lực hoặc null (không tồn tại / đã hết hạn). Không thu hồi phiên. */
    public TokenSessionState findActiveSession(String sessionId) {
        TokenSessionState sessionState = getSession(sessionId);
        return sessionState == null || isExpired(sessionState) ? null : sessionState;
    }

    /** Phiên cũ (chưa có cờ rememberMe) được coi là có ghi nhớ đăng nhập. */
    public boolean isRememberMe(TokenSessionState sessionState) {
        return sessionState.getRememberMe() == null || sessionState.getRememberMe();
    }

    /**
     * Hạn mới của phiên khi được gia hạn lúc này: không hoạt động tối đa idle,
     * nhưng không bao giờ vượt hạn tuyệt đối kể từ lúc đăng nhập.
     */
    public Instant nextExpiry(TokenSessionState sessionState) {
        return slideExpiry(sessionState, Instant.now());
    }

    private Instant slideExpiry(TokenSessionState sessionState, Instant now) {
        boolean rememberMe = isRememberMe(sessionState);
        Instant absolute = sessionState.getAbsoluteExpiresAt();
        if (absolute == null) {
            Instant base = sessionState.getIssuedAt() != null ? sessionState.getIssuedAt() : now;
            absolute = base.plusMillis(absoluteMs(rememberMe));
        }
        Instant idle = now.plusMillis(idleMs(rememberMe));
        return idle.isBefore(absolute) ? idle : absolute;
    }

    private long idleMs(boolean rememberMe) {
        AuthProperties.Jwt jwt = authProperties.getJwt();
        return rememberMe ? jwt.getRememberMeIdleMs() : jwt.getSessionIdleMs();
    }

    private long absoluteMs(boolean rememberMe) {
        AuthProperties.Jwt jwt = authProperties.getJwt();
        return rememberMe ? jwt.getRememberMeAbsoluteMs() : jwt.getSessionAbsoluteMs();
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
            throw invalidSession("Phien dang nhap khong con hieu luc");
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
            throw invalidSession("Phien dang nhap khong con hieu luc");
        }

        rememberAccessToken(sessionState);

        Instant now = Instant.now();
        sessionState.setAccessTokenJti(accessTokenJti);
        sessionState.setRefreshTokenJti(refreshTokenJti);
        sessionState.setRefreshTokenHash(hashRefreshToken(refreshToken));
        sessionState.setExpiresAt(slideExpiry(sessionState, now));
        sessionState.setLastUsedAt(now);
        saveSession(sessionState);
        return sessionState;
    }

    public void rotateRefreshSession(
            String sessionId,
            String username,
            String expectedRefreshTokenJti,
            String expectedRefreshToken,
            String accessTokenJti,
            String refreshTokenJti,
            String refreshToken
    ) {
        String expectedHash = hashRefreshToken(expectedRefreshToken);
        // Hai lần thử: nếu một request refresh khác vừa xoay token trước (CAS thất bại) thì đọc lại phiên rồi xét tiếp,
        // refresh token cũ vẫn dùng được trong thời gian ân hạn ngắn.
        for (int attempt = 0; attempt < 2; attempt++) {
            StoredSession storedSession = getStoredSession(sessionId);
            if (storedSession == null || isExpired(storedSession.state())) {
                revokeSession(sessionId);
                throw invalidSession("Phien dang nhap khong con hieu luc");
            }

            TokenSessionState sessionState = storedSession.state();
            boolean isCurrent = Objects.equals(sessionState.getUsername(), username)
                    && Objects.equals(sessionState.getRefreshTokenJti(), expectedRefreshTokenJti)
                    && Objects.equals(sessionState.getRefreshTokenHash(), expectedHash);
            if (!isCurrent && !(Objects.equals(sessionState.getUsername(), username) && wasRotatedRecently(sessionState, expectedHash))) {
                throw invalidSession("Phien dang nhap khong con hieu luc");
            }

            Instant now = Instant.now();
            rememberAccessToken(sessionState);
            rememberRefreshToken(sessionState, now);
            sessionState.setAccessTokenJti(accessTokenJti);
            sessionState.setRefreshTokenJti(refreshTokenJti);
            sessionState.setRefreshTokenHash(hashRefreshToken(refreshToken));
            sessionState.setExpiresAt(slideExpiry(sessionState, now));
            sessionState.setLastUsedAt(now);

            try {
                Duration ttl = resolveTtl(sessionState.getExpiresAt());
                Long result = stringRedisTemplate.execute(
                        COMPARE_AND_ROTATE_REFRESH_SCRIPT,
                        List.of(sessionKey(sessionId), userSessionsKey(username)),
                        storedSession.raw(),
                        objectMapper.writeValueAsString(sessionState),
                        Long.toString(ttl.toMillis()),
                        sessionId,
                        Double.toString(score(now))
                );
                if (Long.valueOf(1L).equals(result)) {
                    return;
                }
            } catch (JsonProcessingException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Không thể lưu dữ liệu phiên đăng nhập");
            } catch (ApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("Atomic refresh-token rotation failed for session {}", sessionId, exception);
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Hệ thống phiên đăng nhập tạm thời không khả dụng");
            }
        }
        throw invalidSession("Refresh token đã được sử dụng hoặc không còn hiệu lực");
    }

    /**
     * Refresh token vừa bị thay trong vòng {@link #REFRESH_GRACE_MS} vẫn được chấp nhận một lần nữa: trình duyệt có thể đã hủy phản hồi
     * (đổi trang ngay sau khi đăng nhập, hai tab refresh cùng lúc) nên chưa nhận được cookie mới. Token cũ ngoài khoảng này bị từ chối.
     */
    private boolean wasRotatedRecently(TokenSessionState sessionState, String hash) {
        long cutoff = Instant.now().toEpochMilli() - REFRESH_GRACE_MS;
        for (String entry : sessionState.getPreviousRefreshTokens() == null ? List.<String>of() : sessionState.getPreviousRefreshTokens()) {
            int separator = entry.indexOf('@');
            if (separator <= 0) {
                continue;
            }
            try {
                if (entry.substring(0, separator).equals(hash) && Long.parseLong(entry.substring(separator + 1)) >= cutoff) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Bản ghi hỏng: bỏ qua.
            }
        }
        return false;
    }

    private void rememberRefreshToken(TokenSessionState sessionState, Instant now) {
        List<String> entries = sessionState.getPreviousRefreshTokens();
        if (entries == null) {
            entries = new ArrayList<>();
            sessionState.setPreviousRefreshTokens(entries);
        }
        long cutoff = now.toEpochMilli() - REFRESH_GRACE_MS;
        entries.removeIf(entry -> {
            int separator = entry.indexOf('@');
            try {
                return separator <= 0 || Long.parseLong(entry.substring(separator + 1)) < cutoff;
            } catch (NumberFormatException exception) {
                return true;
            }
        });
        if (StringUtils.hasText(sessionState.getRefreshTokenHash())) {
            entries.add(sessionState.getRefreshTokenHash() + "@" + now.toEpochMilli());
        }
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
                || !isKnownAccessToken(sessionState, accessTokenJti)) {
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

    public void revokeAllSessions(String username) {
        for (TokenSessionState session : listActiveSessions(username)) {
            revokeSession(session.getSessionId());
        }
        stringRedisTemplate.delete(userSessionsKey(username));
    }

    /** Thu hồi mọi phiên của người dùng trừ phiên đang dùng. */
    public void revokeOtherSessions(String username, String keepSessionId) {
        if (!StringUtils.hasText(keepSessionId)) {
            revokeAllSessions(username);
            return;
        }
        for (TokenSessionState session : listActiveSessions(username)) {
            if (!keepSessionId.equals(session.getSessionId())) {
                revokeSession(session.getSessionId());
            }
        }
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

    private TokenSessionState requireSession(String sessionId) {
        TokenSessionState sessionState = getSession(sessionId);
        if (sessionState == null || isExpired(sessionState)) {
            revokeSession(sessionId);
            throw invalidSession("Phiên đăng nhập không còn hiệu lực");
        }
        return sessionState;
    }

    private TokenSessionState getSession(String sessionId) {
        StoredSession storedSession = getStoredSession(sessionId);
        return storedSession == null ? null : storedSession.state();
    }

    private StoredSession getStoredSession(String sessionId) {
        String rawSession = readValue(sessionKey(sessionId));
        if (rawSession == null || rawSession.isBlank()) {
            return null;
        }

        try {
            TokenSessionState sessionState = objectMapper.readValue(rawSession, TokenSessionState.class);
            if (sessionState.getPreviousAccessTokenJtis() == null) {
                sessionState.setPreviousAccessTokenJtis(new ArrayList<>());
            }
            return new StoredSession(rawSession, sessionState);
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
        try {
            stringRedisTemplate.opsForZSet().add(
                    userSessionsKey(sessionState.getUsername()),
                    sessionState.getSessionId(),
                    score(touchedAt)
            );
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Hệ thống phiên đăng nhập tạm thời không khả dụng");
        }
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

    private double evictionScore(String username, TokenSessionState sessionState) {
        try {
            Double persistedScore = stringRedisTemplate.opsForZSet().score(
                    userSessionsKey(username),
                    sessionState.getSessionId()
            );
            return persistedScore == null ? score(evictionInstant(sessionState)) : persistedScore;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "Hệ thống phiên đăng nhập tạm thời không khả dụng");
        }
    }

    private double score(Instant instant) {
        return instant == null ? Instant.now().toEpochMilli() : instant.toEpochMilli();
    }

    private void rememberAccessToken(TokenSessionState sessionState) {
        if (!StringUtils.hasText(sessionState.getAccessTokenJti())) {
            return;
        }

        List<String> knownIds = sessionState.getPreviousAccessTokenJtis();
        if (knownIds == null) {
            knownIds = new ArrayList<>();
            sessionState.setPreviousAccessTokenJtis(knownIds);
        }

        knownIds.remove(sessionState.getAccessTokenJti());
        knownIds.add(sessionState.getAccessTokenJti());
        while (knownIds.size() > MAX_TRACKED_ACCESS_TOKEN_IDS) {
            knownIds.remove(0);
        }
    }

    private boolean isKnownAccessToken(TokenSessionState sessionState, String accessTokenJti) {
        if (Objects.equals(sessionState.getAccessTokenJti(), accessTokenJti)) {
            return true;
        }

        List<String> knownIds = sessionState.getPreviousAccessTokenJtis();
        return knownIds != null && knownIds.contains(accessTokenJti);
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
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.INTERNAL_ERROR, "He thong phien dang nhap tam thoi khong kha dung");
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

    private record StoredSession(String raw, TokenSessionState state) {
    }
}
