package phucuong.ht.modules.excelJob;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.excel.ExcelResponses;
import phucuong.ht.common.excel.job.ExcelJobResponse;
import phucuong.ht.common.excel.job.ExcelJobService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Xem tiến độ, hủy và tải kết quả các tác vụ Excel nền của chính mình. Việc tạo tác vụ nằm ở endpoint của từng module (giữ nguyên quyền). */
@RestController
@RequestMapping("/api/excel-jobs")
@RequiredArgsConstructor
public class ExcelJobController {

    private final ExcelJobService excelJobService;

    @GetMapping
    public apiResponse<List<ExcelJobResponse>> listMine(Authentication authentication) {
        return apiResponse.success("Lấy danh sách tác vụ thành công", excelJobService.listMine(authentication));
    }

    @GetMapping("/{id}")
    public apiResponse<ExcelJobResponse> get(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy trạng thái tác vụ thành công", excelJobService.get(id, authentication));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable UUID id, Authentication authentication) {
        ExcelJobService.Download download = excelJobService.download(id, authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(ExcelResponses.XLSX)
                .body(new FileSystemResource(download.path()));
    }

    @PostMapping("/{id}/cancel")
    public apiResponse<ExcelJobResponse> cancel(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Đã gửi yêu cầu hủy tác vụ", excelJobService.cancel(id, authentication));
    }
}
