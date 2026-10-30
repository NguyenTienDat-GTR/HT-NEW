package phucuong.ht.config.audit;

import phucuong.ht.config.AuditScopeContext;

/**
 * Chiến lược xác định phạm vi (giáo phận / giáo hạt / giáo xứ) của một loại tài nguyên được ghi log.
 * Thêm loại tài nguyên mới = khai báo thêm một bean, không phải sửa {@code AuditScopeResolver}.
 */
public interface AuditScopeSource {

    /** Tên tài nguyên trong đường dẫn API, vd. "diocese", "course". */
    String resource();

    /** Phạm vi của tài nguyên; ném IllegalArgumentException nếu mã không hợp lệ (người gọi coi như không xác định). */
    AuditScopeContext resolve(String resourceId);

    @FunctionalInterface
    interface Resolver {
        AuditScopeContext resolve(String resourceId);
    }

    static AuditScopeSource of(String resource, Resolver resolver) {
        return new AuditScopeSource() {
            @Override
            public String resource() {
                return resource;
            }

            @Override
            public AuditScopeContext resolve(String resourceId) {
                return resolver.resolve(resourceId);
            }
        };
    }
}
