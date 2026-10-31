package phucuong.ht.modules.certificate.config.service;

import phucuong.ht.common.layoutconfig.LayoutConfig;
import phucuong.ht.common.layoutconfig.LayoutConfigStore;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver.ActorScope;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Quy tắc dùng chung của thư viện cấu hình theo phạm vi (chứng nhận, thẻ tham dự): ai được thấy/sửa,
 * đặt cấu hình chung, và không xóa cấu hình chung. Mỗi service chỉ còn phần nội dung riêng của loại cấu hình.
 */
public final class LayoutConfigRules {

    private LayoutConfigRules() {
    }

    /** Cấu hình người dùng được thấy; null usableKeys nghĩa là thấy tất cả. */
    public static <E extends LayoutConfig> List<E> visible(LayoutConfigStore<E> store, ActorScope actor) {
        return actor.usableKeys() == null ? store.findAllConfigs() : store.findByScopeKeys(actor.usableKeys());
    }

    /** Cấu hình phải nằm trong phạm vi người dùng thấy và thuộc cấp người dùng được sửa. */
    public static <E extends LayoutConfig> E requireEditable(LayoutConfigStore<E> store, CommonValidationHelper validation, UUID id,
                                                             ActorScope actor, String notFoundMessage) {
        E config = store.findById(id)
                .filter(c -> actor.canSee(c.getScopeKey()))
                .orElseThrow(() -> validation.notFound(notFoundMessage));
        if (!actor.canEdit(config.getScopeKey())) {
            throw validation.forbidden("Cấu hình này thuộc cấp khác, bạn chỉ được xem và dùng, không được sửa hoặc xóa");
        }
        return config;
    }

    /** Đặt cấu hình này làm chung của phạm vi (bỏ cờ của cấu hình chung cũ). Trả về bản đã lưu. */
    public static <E extends LayoutConfig> E makeDefault(LayoutConfigStore<E> store, E config, String audit, OffsetDateTime now) {
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            return config;
        }
        store.findDefault(config.getScopeKey()).ifPresent(current -> {
            current.setIsDefault(Boolean.FALSE);
            current.setUpdatedAt(now);
            current.setUpdatedBy(audit);
            store.merge(current);
        });
        // Chỉ mục duy nhất một cấu hình chung mỗi phạm vi: bỏ cờ cũ xuống DB trước khi đặt cờ mới.
        store.flush();
        config.setIsDefault(Boolean.TRUE);
        config.setUpdatedAt(now);
        config.setUpdatedBy(audit);
        return store.merge(config);
    }

    /** Không xóa được cấu hình chung (phải chọn cấu hình khác làm chung trước). */
    public static void requireNotDefault(LayoutConfig config, CommonValidationHelper validation) {
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            throw validation.conflict(
                    "Không thể xóa cấu hình chung. Hãy chọn một cấu hình khác làm cấu hình chung trước khi xóa.");
        }
    }
}
