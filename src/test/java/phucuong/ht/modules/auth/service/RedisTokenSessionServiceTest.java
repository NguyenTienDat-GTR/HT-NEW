package phucuong.ht.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import phucuong.ht.modules.auth.config.AuthProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisTokenSessionServiceTest {

    private final Map<String, String> valueStore = new HashMap<>();
    private final Map<String, Map<String, Double>> zsetStore = new HashMap<>();

    private RedisTokenSessionService redisTokenSessionService;
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        valueStore.clear();
        zsetStore.clear();

        stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        doAnswer(invocation -> {
            valueStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        when(valueOperations.get(anyString())).thenAnswer(invocation -> valueStore.get(invocation.getArgument(0)));

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            double score = invocation.getArgument(2);
            zsetStore.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(value, score);
            return true;
        }).when(zSetOperations).add(anyString(), anyString(), anyDouble());

        when(zSetOperations.range(anyString(), anyLong(), anyLong())).thenAnswer(invocation ->
                zsetStore.getOrDefault(invocation.getArgument(0), Map.of()).entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
        );

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object rawValues = invocation.getArgument(1);
            Object[] values = rawValues instanceof Object[] objects ? objects : new Object[]{rawValues};
            Map<String, Double> entries = zsetStore.get(key);
            if (entries != null) {
                for (Object value : values) {
                    entries.remove(String.valueOf(value));
                }
            }
            return (long) values.length;
        }).when(zSetOperations).remove(anyString(), any());

        doAnswer(invocation -> {
            valueStore.remove(invocation.getArgument(0));
            return true;
        }).when(stringRedisTemplate).delete(anyString());

        doAnswer(invocation -> true).when(stringRedisTemplate).expire(anyString(), any(Duration.class));

        AuthProperties authProperties = new AuthProperties();
        authProperties.getJwt().setRefreshTokenExpirationMs(604_800_000L);

        redisTokenSessionService = new RedisTokenSessionService(
                stringRedisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                authProperties
        );
    }

    @Test
    void shouldRevokePreviousSessionWhenSameIpLogsInAgain() {
        TokenSessionState first = redisTokenSessionService.createSession(
                "superadminpc",
                "127.0.0.1",
                "access-1",
                "refresh-1",
                "refresh-token-1"
        );

        TokenSessionState second = redisTokenSessionService.createSession(
                "superadminpc",
                "127.0.0.1",
                "access-2",
                "refresh-2",
                "refresh-token-2"
        );

        List<TokenSessionState> sessions = redisTokenSessionService.listActiveSessions("superadminpc");

        assertEquals(1, sessions.size());
        assertFalse(sessions.stream().anyMatch(session -> session.getSessionId().equals(first.getSessionId())));
        assertTrue(sessions.stream().anyMatch(session -> session.getSessionId().equals(second.getSessionId())));
    }

    @Test
    void shouldEvictOldestSessionWhenSessionLimitIsReached() throws InterruptedException {
        TokenSessionState first = redisTokenSessionService.createSession(
                "superadminpc",
                "127.0.0.1",
                "access-1",
                "refresh-1",
                "refresh-token-1"
        );
        Thread.sleep(5);
        TokenSessionState second = redisTokenSessionService.createSession(
                "superadminpc",
                "127.0.0.2",
                "access-2",
                "refresh-2",
                "refresh-token-2"
        );
        Thread.sleep(5);
        TokenSessionState third = redisTokenSessionService.createSession(
                "superadminpc",
                "127.0.0.3",
                "access-3",
                "refresh-3",
                "refresh-token-3"
        );
        List<TokenSessionState> sessions = redisTokenSessionService.listActiveSessions("superadminpc");

        assertEquals(2, sessions.size());
        assertFalse(sessions.stream().anyMatch(session -> session.getSessionId().equals(first.getSessionId())));
        assertTrue(sessions.stream().anyMatch(session -> session.getSessionId().equals(second.getSessionId())));
        assertTrue(sessions.stream().anyMatch(session -> session.getSessionId().equals(third.getSessionId())));
    }

    @Test
    void shouldKeepPreviousAccessTokenActiveAfterRefreshRotation() {
        TokenSessionState session = redisTokenSessionService.createSession(
                "superadminpc",
                "127.0.0.1",
                "access-1",
                "refresh-1",
                "refresh-token-1"
        );

        redisTokenSessionService.rotateSession(
                session.getSessionId(),
                "superadminpc",
                "access-2",
                "refresh-2",
                "refresh-token-2"
        );

        assertTrue(redisTokenSessionService.isAccessTokenActive(session.getSessionId(), "superadminpc", "access-1"));
        assertTrue(redisTokenSessionService.isAccessTokenActive(session.getSessionId(), "superadminpc", "access-2"));
    }

    @Test
    void sessionWithoutRememberMeShouldExpireAfterEightHoursIdleAndTwelveHoursAbsolute() {
        TokenSessionState session = redisTokenSessionService.createSession(
                "superadminpc", "127.0.0.1", "access-1", "refresh-1", "refresh-token-1", false);

        assertFalse(redisTokenSessionService.isRememberMe(session));
        assertNear(java.time.Instant.now().plus(Duration.ofHours(8)), session.getExpiresAt());
        assertNear(java.time.Instant.now().plus(Duration.ofHours(12)), session.getAbsoluteExpiresAt());
    }

    @Test
    void sessionWithRememberMeShouldExpireAfterSevenDaysIdleAndThirtyDaysAbsolute() {
        TokenSessionState session = redisTokenSessionService.createSession(
                "superadminpc", "127.0.0.1", "access-1", "refresh-1", "refresh-token-1", true);

        assertTrue(redisTokenSessionService.isRememberMe(session));
        assertNear(java.time.Instant.now().plus(Duration.ofDays(7)), session.getExpiresAt());
        assertNear(java.time.Instant.now().plus(Duration.ofDays(30)), session.getAbsoluteExpiresAt());
    }

    @Test
    void slidingRenewalShouldNeverPassTheAbsoluteDeadline() {
        java.time.Instant now = java.time.Instant.now();
        TokenSessionState nearlyOver = new TokenSessionState(
                "s1", "superadminpc", "127.0.0.1", "a", new java.util.ArrayList<>(), "r", "h",
                now.minus(Duration.ofDays(29)).minusSeconds(3600), now.plusSeconds(60), now,
                true, now.plus(Duration.ofHours(2)));

        java.time.Instant renewed = redisTokenSessionService.nextExpiry(nearlyOver);

        assertEquals(nearlyOver.getAbsoluteExpiresAt(), renewed);
    }

    @Test
    void slidingRenewalShouldExtendIdleWindowWhileBelowAbsoluteDeadline() {
        java.time.Instant now = java.time.Instant.now();
        TokenSessionState fresh = new TokenSessionState(
                "s2", "superadminpc", "127.0.0.1", "a", new java.util.ArrayList<>(), "r", "h",
                now, now.plusSeconds(60), now, false, now.plus(Duration.ofHours(12)));

        assertNear(now.plus(Duration.ofHours(8)), redisTokenSessionService.nextExpiry(fresh));
    }

    @Test
    void legacySessionWithoutRememberMeFieldsShouldBeTreatedAsRememberedWithAbsoluteFromIssueTime() {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant issuedAt = now.minus(Duration.ofDays(29));
        TokenSessionState legacy = new TokenSessionState(
                "s3", "superadminpc", "127.0.0.1", "a", new java.util.ArrayList<>(), "r", "h",
                issuedAt, now.plusSeconds(60), now, null, null);

        assertTrue(redisTokenSessionService.isRememberMe(legacy));
        assertEquals(issuedAt.plus(Duration.ofDays(30)), redisTokenSessionService.nextExpiry(legacy));
    }

    @Test
    void findActiveSessionShouldReturnNullForUnknownOrExpiredSession() {
        assertEquals(null, redisTokenSessionService.findActiveSession("missing"));
        TokenSessionState session = redisTokenSessionService.createSession(
                "superadminpc", "127.0.0.1", "access-1", "refresh-1", "refresh-token-1", true);

        assertEquals(session.getSessionId(), redisTokenSessionService.findActiveSession(session.getSessionId()).getSessionId());
    }

    private static void assertNear(java.time.Instant expected, java.time.Instant actual) {
        long diffMs = Math.abs(Duration.between(expected, actual).toMillis());
        assertTrue(diffMs < 5_000, "Lệch " + diffMs + "ms: " + expected + " vs " + actual);
    }

    @Test
    void shouldAllowOnlyOneRefreshRotationWhenAtomicCompareAndSetIsContended() {
        TokenSessionState session = redisTokenSessionService.createSession(
                "superadminpc", "127.0.0.1", "access-1", "refresh-1", "refresh-token-1");
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        redisTokenSessionService.rotateRefreshSession(
                session.getSessionId(), "superadminpc", "refresh-1", "refresh-token-1",
                "access-2", "refresh-2", "refresh-token-2");

        assertThrows(RuntimeException.class, () -> redisTokenSessionService.rotateRefreshSession(
                session.getSessionId(), "superadminpc", "refresh-1", "refresh-token-1",
                "access-3", "refresh-3", "refresh-token-3"));
    }

    @Test
    void staleRefreshTokenShouldStillWorkWithinGraceWindowButNotAfterIt() {
        TokenSessionState session = redisTokenSessionService.createSession(
                "superadminpc", "127.0.0.1", "access-1", "refresh-1", "refresh-token-1");
        redisTokenSessionService.rotateSession(session.getSessionId(), "superadminpc", "access-1", "refresh-1", "refresh-token-1");
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    @SuppressWarnings("unchecked")
                    List<String> keys = (List<String>) args[1];
                    if (!args[2].equals(valueStore.get(keys.get(0)))) {
                        return 0L;
                    }
                    valueStore.put(keys.get(0), (String) args[3]);
                    return 1L;
                });

        redisTokenSessionService.rotateRefreshSession(session.getSessionId(), "superadminpc", "refresh-1", "refresh-token-1",
                "access-2", "refresh-2", "refresh-token-2");
        // Phản hồi của lần xoay đầu bị trình duyệt hủy nên cookie vẫn là token cũ: dùng lại vẫn được trong thời gian ân hạn.
        redisTokenSessionService.rotateRefreshSession(session.getSessionId(), "superadminpc", "refresh-1", "refresh-token-1",
                "access-3", "refresh-3", "refresh-token-3");
        // Token đã bị thay quá thời gian ân hạn thì bị từ chối.
        String key = "auth:session:" + session.getSessionId();
        valueStore.put(key, valueStore.get(key).replaceAll("@[0-9]+", "@1000"));
        assertThrows(RuntimeException.class, () -> redisTokenSessionService.rotateRefreshSession(session.getSessionId(), "superadminpc",
                "refresh-1", "refresh-token-1", "access-4", "refresh-4", "refresh-token-4"));
    }
}
