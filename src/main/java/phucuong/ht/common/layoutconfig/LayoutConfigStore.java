package phucuong.ht.common.layoutconfig;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Những thao tác kho dữ liệu mà các quy tắc chung của thư viện cấu hình cần. */
public interface LayoutConfigStore<E extends LayoutConfig> {

    Optional<E> findById(UUID id);

    Optional<E> findDefault(String scopeKey);

    List<E> findAllConfigs();

    List<E> findByScopeKeys(Collection<String> scopeKeys);

    E merge(E config);

    void flush();

    void remove(UUID id);
}
