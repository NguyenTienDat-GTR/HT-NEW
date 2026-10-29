package phucuong.ht.modules.media.album;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import phucuong.ht.common.dto.apiResponse;

import java.io.IOException;
import java.util.UUID;

/** Album ảnh nền của chứng nhận (kind = CERTIFICATE) và thẻ tham dự (kind = BADGE) của đơn vị đang đăng nhập. */
@RestController
@RequestMapping("/api/media/albums/{kind}")
@RequiredArgsConstructor
public class BackgroundAlbumController {

    private final BackgroundAlbumService albumService;

    @GetMapping
    @PreAuthorize("@albumAccessEvaluator.canRead(authentication, #kind)")
    public apiResponse<BackgroundAlbumService.AlbumView> list(@PathVariable AlbumKind kind, Authentication authentication) {
        return apiResponse.success("Lấy album ảnh nền thành công", albumService.list(kind, authentication));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@albumAccessEvaluator.canWrite(authentication, #kind)")
    public apiResponse<BackgroundAlbumService.AlbumItemView> add(
            @PathVariable AlbumKind kind, @RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        return apiResponse.success("Đã thêm ảnh vào album", albumService.add(kind, file.getBytes(), authentication));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("@albumAccessEvaluator.canWrite(authentication, #kind)")
    public apiResponse<Void> delete(@PathVariable AlbumKind kind, @PathVariable UUID itemId, Authentication authentication) {
        albumService.delete(kind, itemId, authentication);
        return apiResponse.success("Đã xóa ảnh khỏi album", null);
    }
}
