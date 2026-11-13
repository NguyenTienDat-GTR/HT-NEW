package phucuong.ht.modules.training.registrationTemplate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.excel.ExcelResponses;
import phucuong.ht.modules.training.registrationTemplate.service.RegistrationTemplateService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Mẫu danh sách đăng ký của giáo phận / giáo hạt: xem, tải file, lưu (file do trình soạn trên trình duyệt tạo), xóa.
 * unitType + unitId chỉ cần cho super admin; tài khoản luôn làm việc với đơn vị của mình.
 */
@RestController
@RequestMapping("/api/training/registration-template")
@RequiredArgsConstructor
public class RegistrationTemplateController {

    private final RegistrationTemplateService service;

    @GetMapping
    @PreAuthorize("@registrationTemplateAccessEvaluator.canRead(authentication)")
    public apiResponse<RegistrationTemplateService.TemplateResponse> get(@RequestParam(required = false) String unitType,
                                                                        @RequestParam(required = false) UUID unitId, Authentication authentication) {
        return apiResponse.success("Lấy mẫu danh sách đăng ký thành công", service.get(unitType, unitId, authentication));
    }

    @GetMapping("/fields")
    @PreAuthorize("@registrationTemplateAccessEvaluator.canRead(authentication)")
    public apiResponse<List<RegistrationTemplateService.FieldResponse>> fields() {
        return apiResponse.success("Lấy danh sách trường thành công", service.fields());
    }

    @GetMapping("/file")
    @PreAuthorize("@registrationTemplateAccessEvaluator.canRead(authentication)")
    public ResponseEntity<byte[]> file(@RequestParam(required = false) String unitType, @RequestParam(required = false) UUID unitId,
                                       Authentication authentication) {
        RegistrationTemplateService.TemplateFile file = service.file(unitType, unitId, authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(ExcelResponses.XLSX)
                .contentLength(file.content().length)
                .body(file.content());
    }

    @PutMapping(value = "/file", consumes = "multipart/form-data")
    @PreAuthorize("@registrationTemplateAccessEvaluator.canUpdate(authentication)")
    public apiResponse<RegistrationTemplateService.TemplateResponse> save(@RequestParam(required = false) String unitType,
                                                                         @RequestParam(required = false) UUID unitId,
                                                                         @RequestPart("file") MultipartFile file,
                                                                         Authentication authentication) {
        return apiResponse.success("Đã lưu mẫu danh sách đăng ký", service.save(unitType, unitId, file, authentication));
    }

    @DeleteMapping
    @PreAuthorize("@registrationTemplateAccessEvaluator.canUpdate(authentication)")
    public apiResponse<Void> delete(@RequestParam(required = false) String unitType, @RequestParam(required = false) UUID unitId,
                                    Authentication authentication) {
        service.delete(unitType, unitId, authentication);
        return apiResponse.success("Đã xóa mẫu danh sách đăng ký", null);
    }
}
