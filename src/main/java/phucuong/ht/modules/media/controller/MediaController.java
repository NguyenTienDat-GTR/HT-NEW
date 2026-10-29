package phucuong.ht.modules.media.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.entity.MediaVariantInfo;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.model.enums.MediaVariants;
import phucuong.ht.modules.media.model.response.MediaUploadResponse;
import phucuong.ht.modules.media.repository.MediaAssetRepository;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.service.MediaUrlSigner;
import phucuong.ht.modules.media.service.MediaUrls;
import phucuong.ht.modules.media.storage.MediaStorageException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private static final long MAX_BROWSER_CACHE_SECONDS = 3600;

    private final MediaAssetService mediaAssetService;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUrlSigner signer;
    private final MediaUrls mediaUrls;
    private final MediaProperties properties;
    private final securityUtils securityUtils;

    /** Tải ảnh lên (đã được frontend nén sơ bộ). Ảnh ở trạng thái PENDING cho tới khi gắn vào bản ghi. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mediaAccessEvaluator.canUpload(authentication, #category)")
    public apiResponse<MediaUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") MediaCategory category,
            Authentication authentication) throws IOException {
        MediaAsset asset = mediaAssetService.upload(file.getBytes(), category, securityUtils.currentAuditActor(authentication));
        MediaVariantInfo display = asset.variant(category.getDisplayVariant()).orElseThrow();
        return apiResponse.success("Tải ảnh lên thành công", MediaUploadResponse.builder()
                .assetId(asset.getId())
                .category(category)
                .url(mediaUrls.display(asset.getId(), category))
                .thumbUrl(mediaUrls.small(asset.getId(), category))
                .width(display.width())
                .height(display.height())
                .bytes(display.bytes())
                .build());
    }

    /**
     * Phát ảnh. Yêu cầu đăng nhập (đã được bộ lọc JWT kiểm tra) VÀ chữ ký đúng; link không hết hạn nhưng người ngoài chưa đăng nhập thì không xem được.
     */
    @GetMapping("/{assetId}/{variant}")
    public ResponseEntity<byte[]> get(
            @PathVariable UUID assetId,
            @PathVariable String variant,
            @RequestParam(value = "sig", required = false) String sig,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        if (!MediaVariants.ALL.contains(variant) || !signer.verify(assetId, variant, sig)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Liên kết ảnh không hợp lệ");
        }
        MediaAsset asset = mediaAssetService.find(assetId);
        if (asset == null || asset.getStatus() == MediaStatus.DELETED) {
            throw new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ảnh");
        }
        MediaVariantInfo info = asset.variant(variant)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ảnh"));

        CacheControl cacheControl = CacheControl.maxAge(Duration.ofSeconds(MAX_BROWSER_CACHE_SECONDS)).cachePrivate();
        String etag = "\"" + info.sha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cacheControl).build();
        }

        byte[] data;
        try {
            data = mediaAssetService.loadVariant(asset, variant);
        } catch (MediaStorageException e) {
            HttpStatus status = e.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            throw new ApiException(status, e.isNotFound() ? errorCode.RESOURCE_NOT_FOUND : errorCode.INTERNAL_ERROR, "Không tải được ảnh");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.contentType()))
                .contentLength(data.length)
                .eTag(etag)
                .cacheControl(cacheControl)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(data);
    }

    /** Thống kê tiến độ chuyển dữ liệu (nhà cung cấp × trạng thái) cho quản trị viên hệ thống. */
    @GetMapping("/admin/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public apiResponse<List<StatusRow>> status() {
        List<StatusRow> rows = mediaAssetRepository.countByProviderAndStatus().stream()
                .map(r -> new StatusRow(r.getProvider(), r.getStatus().name(), r.getTotal()))
                .toList();
        return apiResponse.success("Thống kê ảnh", rows);
    }

    public record StatusRow(String provider, String status, long total) {
    }
}
