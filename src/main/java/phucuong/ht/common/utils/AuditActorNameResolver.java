package phucuong.ht.common.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * createdBy/updatedBy (và các cột người thao tác khác như assignedBy, revokedBy, approvedBy) lưu
 * mã huynh trưởng (UUID), "super-admin", username tài khoản hoặc tác nhân hệ thống ("SYSTEM",
 * "SYSTEM_AUTO_APPROVAL", ...). Lớp này chuyển giá trị đó thành tên hiển thị.
 * <p>
 * Có cache trong bộ nhớ (TTL 60 giây) để các API danh sách nhiều dòng không phải truy vấn DB cho từng dòng.
 */
@Component
@RequiredArgsConstructor
public class AuditActorNameResolver {

    private static final String SUPER_ADMIN_ACTOR = "super-admin";
    private static final Map<String, String> SYSTEM_ACTOR_NAMES = Map.of(
            "SYSTEM", "Hệ thống",
            "SYSTEM_AUTO_APPROVAL", "Hệ thống tự duyệt",
            "SYSTEM_AUTO_REGISTRATION", "Hệ thống tự động mở/đóng đăng ký",
            "system:permission-expiry-revocation", "Hệ thống tự thu hồi quyền hết hạn",
            "system:log-retention", "Hệ thống dọn nhật ký"
    );
    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final int CACHE_CLEANUP_THRESHOLD = 2_000;

    private final LeaderRepository leaderRepository;
    private final AccountRepository accountRepository;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public String resolve(String actor) {
        if (actor == null || actor.isBlank()) {
            return null;
        }
        if (SUPER_ADMIN_ACTOR.equals(actor)) {
            return "Quản trị tối cao";
        }
        String systemName = SYSTEM_ACTOR_NAMES.get(actor);
        if (systemName != null) {
            return systemName;
        }
        if (actor.startsWith("SYSTEM_") || actor.startsWith("system:")) {
            return "Hệ thống";
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(actor);
        if (cached != null && cached.expiresAt() > now) {
            return cached.value();
        }

        String resolved = lookup(actor);
        if (cache.size() >= CACHE_CLEANUP_THRESHOLD) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            if (cache.size() >= CACHE_CLEANUP_THRESHOLD) {
                cache.clear();
            }
        }
        cache.put(actor, new CacheEntry(resolved, now + CACHE_TTL_MILLIS));
        return resolved;
    }

    /** Xóa cache (dùng cho test hoặc khi cần làm mới ngay). */
    public void evictAll() {
        cache.clear();
    }

    private String lookup(String actor) {
        Optional<UUID> leaderId = parseUuid(actor);
        if (leaderId.isPresent()) {
            return leaderRepository.findById(leaderId.get())
                    .map(AuditActorNameResolver::displayName)
                    .orElse(actor);
        }
        return accountRepository.findById(actor)
                .map(account -> account.getLeader() == null ? null : displayName(account.getLeader()))
                .filter(name -> !name.isBlank())
                .orElse(actor);
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static String displayName(Leader leader) {
        String firstName = leader.getFirstName() == null ? "" : leader.getFirstName();
        String name = leader.getLastName() == null || leader.getLastName().isBlank()
                ? firstName
                : leader.getLastName() + " " + firstName;
        String full = leader.getHolyName() == null || leader.getHolyName().isBlank() ? name : leader.getHolyName() + " " + name;
        return full.trim();
    }

    private record CacheEntry(String value, long expiresAt) {
    }
}
