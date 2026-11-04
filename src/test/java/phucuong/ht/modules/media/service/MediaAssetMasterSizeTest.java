package phucuong.ht.modules.media.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.entity.MediaVariantInfo;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.model.enums.MediaVariants;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;

/** Độ nét của ảnh nền phải đo trên ảnh gốc dùng để in (master), không phải bản xem trước đã thu nhỏ. */
class MediaAssetMasterSizeTest {

    private final UUID id = UUID.randomUUID();

    private MediaAssetService serviceReturning(MediaAsset asset) {
        MediaAssetService service = mock(MediaAssetService.class, CALLS_REAL_METHODS);
        doReturn(asset).when(service).find(id);
        return service;
    }

    private MediaAsset asset(MediaStatus status) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setStatus(status);
        asset.setVariants(List.of(
                new MediaVariantInfo(MediaVariants.MASTER, "m", "image/jpeg", 100, 3508, 2480, "a"),
                new MediaVariantInfo(MediaVariants.PREVIEW, "p", "image/jpeg", 10, 1600, 1131, "b")));
        return asset;
    }

    @Test
    void reportsTheMasterNotThePreviewSize() {
        assertArrayEquals(new int[]{3508, 2480}, serviceReturning(asset(MediaStatus.ACTIVE)).masterSize(id));
    }

    @Test
    void unknownOrDeletedAssetsHaveNoSize() {
        assertNull(serviceReturning(null).masterSize(id));
        assertNull(serviceReturning(asset(MediaStatus.DELETED)).masterSize(id));
        assertNull(serviceReturning(null).masterSize(null));
    }
}
