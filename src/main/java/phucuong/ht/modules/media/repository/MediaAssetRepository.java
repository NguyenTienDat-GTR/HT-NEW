package phucuong.ht.modules.media.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.enums.MediaStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByStatusAndCreatedAtBefore(MediaStatus status, OffsetDateTime before, Pageable pageable);

    List<MediaAsset> findByStatusAndDeletedAtBefore(MediaStatus status, OffsetDateTime before, Pageable pageable);

    /** Asset ACTIVE đang ở nhà cung cấp khác nhà cung cấp hiện hành và đến hạn thử lại (cho job chuyển nhà cung cấp). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select a from MediaAsset a
            where a.status = phucuong.ht.modules.media.model.enums.MediaStatus.ACTIVE
              and a.provider <> :provider
              and (a.nextAttemptAt is null or a.nextAttemptAt <= :now)
              and a.migrationRetries < :maxRetries
            order by a.createdAt
            """)
    List<MediaAsset> findToMigrate(@Param("provider") String provider, @Param("now") OffsetDateTime now,
                                   @Param("maxRetries") int maxRetries, Pageable pageable);

    /** Asset ACTIVE tạo trước {@code before} nhưng không còn bản ghi nghiệp vụ nào tham chiếu. */
    @Query(value = """
            select a.* from media_asset a
            where a.status = 'ACTIVE' and a.created_at < :before
              and not exists (select 1 from leader l where l.image_asset_id = a.id)
              and not exists (select 1 from account ac where ac.image_asset_id = a.id)
              and not exists (select 1 from certificate_config c where c.background_asset_id = a.id)
              and not exists (select 1 from badge_config b where b.front_background_asset_id = a.id or b.back_background_asset_id = a.id)
              and not exists (select 1 from background_album_item i where i.asset_id = a.id)
            order by a.created_at
            limit :limit
            """, nativeQuery = true)
    List<MediaAsset> findUnreferencedActive(@Param("before") OffsetDateTime before, @Param("limit") int limit);

    @Query("select a.provider as provider, a.status as status, count(a) as total from MediaAsset a group by a.provider, a.status")
    List<StatusCount> countByProviderAndStatus();

    interface StatusCount {
        String getProvider();

        MediaStatus getStatus();

        long getTotal();
    }
}
