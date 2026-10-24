package phucuong.ht.modules.badge.config.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.badge.config.model.entity.BadgeConfig;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BadgeConfigRepository extends BaseRepo<BadgeConfig, UUID> {

    public BadgeConfigRepository(EntityManager entityManager) {
        super(entityManager, BadgeConfig.class);
    }

    @Transactional(readOnly = true)
    public List<BadgeConfig> findByScopeKeys(Collection<String> scopeKeys) {
        return entityManager.createQuery(
                        "select c from BadgeConfig c where c.scopeKey in :keys order by c.isDefault desc, lower(c.name)",
                        BadgeConfig.class)
                .setParameter("keys", scopeKeys)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<BadgeConfig> findAllConfigs() {
        return entityManager.createQuery(
                        "select c from BadgeConfig c order by c.scopeKey, c.isDefault desc, lower(c.name)",
                        BadgeConfig.class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<BadgeConfig> findDefault(String scopeKey) {
        return entityManager.createQuery(
                        "select c from BadgeConfig c where c.scopeKey = :key and c.isDefault = true",
                        BadgeConfig.class)
                .setParameter("key", scopeKey)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<String> findNamesInScope(String scopeKey) {
        return entityManager.createQuery(
                        "select c.name from BadgeConfig c where c.scopeKey = :key", String.class)
                .setParameter("key", scopeKey)
                .getResultList();
    }

    @Transactional
    public BadgeConfig persist(BadgeConfig config) {
        entityManager.persist(config);
        return config;
    }

    @Transactional
    public BadgeConfig merge(BadgeConfig config) {
        return entityManager.merge(config);
    }

    @Transactional
    public void flush() {
        entityManager.flush();
    }

    @Transactional
    public void remove(UUID id) {
        BadgeConfig managed = entityManager.find(BadgeConfig.class, id);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
