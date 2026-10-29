package phucuong.ht.modules.media.album;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver.ActorScope;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.service.MediaFolderService;
import phucuong.ht.modules.media.service.MediaUrls;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Album ảnh nền của chứng nhận / thẻ. Mỗi đơn vị (giáo phận, giáo hạt, hệ thống) có album riêng, tối đa
 * {@code app.media.album-max-items} ảnh cho mỗi loại, và CHỈ thấy / dùng / xóa album của chính mình
 * (giáo phận không thấy album của giáo hạt và ngược lại). Cấu hình lấy ảnh từ album bằng cách sao chép.
 */
@Service
@RequiredArgsConstructor
public class BackgroundAlbumService {

    private final BackgroundAlbumRepository repository;
    private final CertificateConfigScopeResolver scopeResolver;
    private final MediaAssetService assets;
    private final MediaFolderService folders;
    private final MediaUrls mediaUrls;
    private final MediaProperties properties;
    private final securityUtils securityUtils;
    private final JdbcTemplate jdbc;

    public record AlbumItemView(UUID id, UUID assetId, String url, OffsetDateTime createdAt) {
    }

    public record AlbumView(int maxItems, List<AlbumItemView> items) {
    }

    @Transactional(readOnly = true)
    public AlbumView list(AlbumKind kind, Authentication authentication) {
        ActorScope actor = resolve(kind, authentication);
        List<AlbumItemView> items = repository.findByScopeKeyAndAlbumKindOrderByCreatedAtAsc(actor.key(), kind.name()).stream()
                .map(item -> view(item, kind))
                .toList();
        return new AlbumView(properties.getAlbumMaxItems(), items);
    }

    /** Thêm ảnh vào album của đơn vị. Đủ số ảnh tối đa thì từ chối: phải xóa bớt một ảnh trước. */
    @Transactional
    public AlbumItemView add(AlbumKind kind, byte[] image, Authentication authentication) {
        ActorScope actor = resolve(kind, authentication);
        // Khóa theo (đơn vị, loại) để hai lần tải đồng thời không vượt quá giới hạn.
        jdbc.queryForObject("select 1 from (select pg_advisory_xact_lock(hashtext(?))) t", Integer.class, actor.key() + "|" + kind.name());
        long count = repository.countByScopeKeyAndAlbumKind(actor.key(), kind.name());
        int max = properties.getAlbumMaxItems();
        if (count >= max) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                    "Album ảnh nền " + label(kind) + " đã đủ " + max + " ảnh. Hãy xóa bớt một ảnh trước khi thêm ảnh mới");
        }
        String actorName = securityUtils.currentAuditActor(authentication);
        MediaAsset asset = assets.createActive(image, kind.getCategory(), folders.dioceseOfScopeKey(actor.key()), actorName);

        BackgroundAlbumItem item = new BackgroundAlbumItem();
        item.setId(UUID.randomUUID());
        item.setScopeKey(actor.key());
        item.setCategory(kind.getCategory());
        item.setAlbumKind(kind.name());
        item.setAssetId(asset.getId());
        item.setCreatedBy(actorName);
        item.setCreatedAt(OffsetDateTime.now());
        return view(repository.saveAndFlush(item), kind);
    }

    /** Xóa một ảnh khỏi album. Cấu hình đang dùng ảnh này không bị ảnh hưởng vì mỗi cấu hình giữ bản sao riêng. */
    @Transactional
    public void delete(AlbumKind kind, UUID itemId, Authentication authentication) {
        ActorScope actor = resolve(kind, authentication);
        // Ảnh của đơn vị khác được báo "không tìm thấy" để không lộ việc nó có tồn tại.
        BackgroundAlbumItem item = repository.findByIdAndScopeKeyAndAlbumKind(itemId, actor.key(), kind.name())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ảnh trong album"));
        repository.delete(item);
        repository.flush();
        assets.release(item.getAssetId());
    }

    /**
     * Ảnh đã lưu (ACTIVE) chỉ được đưa vào cấu hình nếu thuộc album của chính đơn vị, hoặc đang là ảnh của một cấu hình
     * mà đơn vị được xem (trường hợp "lưu thành cấu hình mới"). Ảnh album của đơn vị khác không dùng được dù biết mã.
     */
    @Transactional(readOnly = true)
    public void assertUsable(UUID assetId, ActorScope actor) {
        MediaAsset asset = assets.find(assetId);
        if (asset == null || asset.getStatus() != MediaStatus.ACTIVE) {
            return; // Ảnh mới tải lên (PENDING) do MediaAssetService kiểm tra người tải.
        }
        if (actor.usableKeys() == null || repository.existsByAssetIdAndScopeKey(assetId, actor.key())) {
            return;
        }
        List<String> usable = actor.usableKeys();
        Boolean inVisibleConfig = jdbc.query("""
                select exists (
                    select 1 from certificate_config c where c.background_asset_id = ? and c.scope_key = any (?)
                    union all
                    select 1 from badge_config b where (b.front_background_asset_id = ? or b.back_background_asset_id = ?) and b.scope_key = any (?)
                )
                """, ps -> {
            ps.setObject(1, assetId);
            ps.setArray(2, ps.getConnection().createArrayOf("varchar", usable.toArray()));
            ps.setObject(3, assetId);
            ps.setObject(4, assetId);
            ps.setArray(5, ps.getConnection().createArrayOf("varchar", usable.toArray()));
        }, rs -> rs.next() && rs.getBoolean(1));
        if (!Boolean.TRUE.equals(inVisibleConfig)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Bạn không có quyền dùng ảnh nền này");
        }
    }

    private static String label(AlbumKind kind) {
        return switch (kind) {
            case CERTIFICATE -> "chứng nhận";
            case BADGE_FRONT -> "mặt trước thẻ";
            case BADGE_BACK -> "mặt sau thẻ";
        };
    }

    private ActorScope resolve(AlbumKind kind, Authentication authentication) {
        return scopeResolver.resolve(authentication, kind.getPermissionPrefix(), "Bạn không có quyền với album ảnh nền");
    }

    private AlbumItemView view(BackgroundAlbumItem item, AlbumKind kind) {
        return new AlbumItemView(item.getId(), item.getAssetId(), mediaUrls.display(item.getAssetId(), kind.getCategory()), item.getCreatedAt());
    }
}
