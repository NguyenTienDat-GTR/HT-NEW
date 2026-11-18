package phucuong.ht.modules.media.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.repository.MediaAssetRepository;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.storage.MediaStorageException;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Vòng dọn: PENDING quá hạn bị xóa ngay (không qua DELETED), xử lý nhiều lượt khi tồn đọng. */
class MediaMaintenanceServiceTest {

    private MediaAssetRepository repository;
    private MediaAssetService assets;
    private MediaMaintenanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        assets = mock(MediaAssetService.class);
        MediaProperties properties = new MediaProperties();
        properties.getCleanup().setBatchSize(2);
        service = new MediaMaintenanceService(repository, assets, properties, mock(JdbcTemplate.class), mock(MediaStorageRegistry.class));
        when(repository.findUnreferencedActive(any(), Mockito.anyInt())).thenReturn(List.of());
        when(repository.findByStatusAndDeletedAtBefore(eq(MediaStatus.DELETED), any(OffsetDateTime.class), any(Pageable.class))).thenReturn(List.of());
    }

    private static MediaAsset pending() {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setStatus(MediaStatus.PENDING);
        return asset;
    }

    @Test
    void stalePendingIsPurgedAndDeletedImmediately() {
        MediaAsset a = pending();
        when(repository.findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(a));

        service.cleanup();

        verify(assets).purgeFiles(a);
        verify(repository).delete(a);
        verify(assets, never()).release(any());
    }

    @Test
    void backlogLargerThanOneBatchIsDrainedInSeveralRounds() {
        MediaAsset a = pending();
        MediaAsset b = pending();
        MediaAsset c = pending();
        when(repository.findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(a, b))
                .thenReturn(List.of(c));

        service.cleanup();

        verify(repository, times(3)).delete(any(MediaAsset.class));
        verify(assets, never()).release(any());
    }

    @Test
    void failedFileDeletionFallsBackToDeletedSoItIsRetriedLater() {
        MediaAsset bad = pending();
        MediaAsset good = pending();
        when(repository.findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(bad, good))
                .thenReturn(List.of());
        doThrow(new MediaStorageException("lỗi mạng", false)).when(assets).purgeFiles(bad);

        service.cleanup();

        verify(repository, never()).delete(bad);
        verify(assets).release(bad.getId());
        verify(repository).delete(good);
    }

    @Test
    void sweepHelpersParseAssetIdFromKey() {
        UUID id = UUID.randomUUID();
        assertEquals(id, MediaSweepService.assetIdOf("c-lead/prod/pending/avatars/" + id + "/thumb.jpg"));
        assertNull(MediaSweepService.assetIdOf("c-lead/prod/pending/avatars/thumb.jpg"));
        assertNull(MediaSweepService.assetIdOf("thumb.jpg"));
        assertNull(MediaSweepService.parseUuid("not-a-uuid"));
    }
}
