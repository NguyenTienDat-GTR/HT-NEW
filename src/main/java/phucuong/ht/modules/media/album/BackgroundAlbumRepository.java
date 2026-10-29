package phucuong.ht.modules.media.album;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackgroundAlbumRepository extends JpaRepository<BackgroundAlbumItem, UUID> {

    List<BackgroundAlbumItem> findByScopeKeyAndAlbumKindOrderByCreatedAtAsc(String scopeKey, String albumKind);

    long countByScopeKeyAndAlbumKind(String scopeKey, String albumKind);

    Optional<BackgroundAlbumItem> findByIdAndScopeKeyAndAlbumKind(UUID id, String scopeKey, String albumKind);

    boolean existsByAssetIdAndScopeKey(UUID assetId, String scopeKey);
}
