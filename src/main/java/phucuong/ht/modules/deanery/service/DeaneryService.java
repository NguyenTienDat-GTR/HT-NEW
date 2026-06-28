package phucuong.ht.modules.deanery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.OrganizationUnitCodeGenerator;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeaneryService {

    private static final String DIOCESE_FILTER_FIELD = "diocese.dioceseCode";
    private static final String DEANERY_FILTER_FIELD = "deaneryCode";

    private final DeaneryRepository deaneryRepository;
    private final OrganizationUnitCodeGenerator organizationUnitCodeGenerator;
    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    public DeaneryResponse create(DeaneryCreateRequest request, Authentication authentication) {
        // Chan ngay tu service de admin giao phan khong the tao giao hat ngoai pham vi cua minh.
        enforceCreateScope(request, authentication);

        String auditActor = getRequiredAuditActor(authentication);
        try {
            // deaneryCode duoc sinh tu dioceseCode + associationName de on dinh va de doc.
            String deaneryCode = organizationUnitCodeGenerator.generateDeaneryCode(
                    request.getDioceseCode(),
                    request.getAssociationName(),
                    deaneryRepository::existsByDeaneryCode
            );
            Deanery deanery = new Deanery();
            deanery.setDeaneryCode(deaneryCode);
            // Gan reference toi Diocese bang ma thay vi query full entity.
            deanery.setDiocese(deaneryRepository.getDioceseReference(request.getDioceseCode()));
            deanery.setName(request.getName());
            deanery.setPatronSaintDay(request.getPatronSaintDay());
            deanery.setEstablishmentDate(request.getEstablishmentDate());
            deanery.setChaplain(request.getChaplain());
            deanery.setPhoneNumber(request.getPhoneNumber());
            deanery.setEmail(request.getEmail());
            deanery.setAssociationName(request.getAssociationName());
            deanery.setStatus(Boolean.TRUE);
            deanery.setCreatedAt(OffsetDateTime.now());
            deanery.setUpdatedAt(deanery.getCreatedAt());
            deanery.setCreatedBy(auditActor);
            deanery.setUpdatedBy(auditActor);
            deaneryRepository.save(deanery);
            return toDeaneryResponse(getExistingDeaneryWithDiocese(deaneryCode));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo hạt bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    public Page<DeaneryResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        // Copy request truoc de co the ep filter theo scope ma khong lam ban object bind tu controller.
        BaseSearchRequest scopedRequest = copyRequest(request);

        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_ALL)) {
            return loadDeaneryPageWithDiocese(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DIOCESE)) {
            String dioceseCode = getRequiredActiveDioceseCode(authentication, DeaneryAccessEvaluator.READ_DIOCESE);
            // Admin giao phan chi thay cac giao hat thuoc giao phan hien tai, ke ca khi client tu gui filter khac.
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseCode);
            return loadDeaneryPageWithDiocese(scopedRequest);
        }

        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.READ_DEANERY)) {
            String deaneryCode = getRequiredActiveDeaneryCode(authentication, DeaneryAccessEvaluator.READ_DEANERY);
            // Admin giao hat chi thay dung giao hat cua minh.
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryCode);
            return loadDeaneryPageWithDiocese(scopedRequest);
        }

        throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách giáo hạt");
    }

    public DeaneryResponse findById(String deaneryCode) {
        return toDeaneryResponse(getExistingDeaneryWithDiocese(deaneryCode));
    }

    public DeaneryResponse update(String deaneryCode, DeaneryUpdateRequest request, Authentication authentication) {
        validateDeaneryCode(deaneryCode);
        // Chi can dam bao giao hat ton tai truoc khi update, API nay khong cho phep doi dioceseCode.
        getExistingDeanery(deaneryCode);
        String auditActor = getRequiredAuditActor(authentication);
        try {
            deaneryRepository.update(deaneryCode, request, auditActor);
            return toDeaneryResponse(getExistingDeaneryWithDiocese(deaneryCode));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo hạt bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    public void delete(String deaneryCode, Authentication authentication) {
        validateDeaneryCode(deaneryCode);
        Deanery deanery = getExistingDeanery(deaneryCode);
        // Delete la quyen nhay cam nen kiem tra lai scope tai service thay vi chi tin vao evaluator.
        enforceDeleteScope(authentication, deanery);
        try {
            deaneryRepository.delete(deaneryCode);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Không thể xóa giáo hạt vì đang được dữ liệu khác tham chiếu");
        }
    }

    private Deanery getExistingDeanery(String deaneryCode) {
        validateDeaneryCode(deaneryCode);
        return deaneryRepository.findById(deaneryCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        errorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy giáo hạt: " + deaneryCode
                ));
    }

    private Deanery getExistingDeaneryWithDiocese(String deaneryCode) {
        validateDeaneryCode(deaneryCode);
        return deaneryRepository.findByIdWithDiocese(deaneryCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        errorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy giáo hạt: " + deaneryCode
                ));
    }

    private void validateDeaneryCode(String deaneryCode) {
        if (!StringUtils.hasText(deaneryCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mã giáo hạt là bắt buộc");
        }
    }

    private String getRequiredAuditActor(Authentication authentication) {
        String auditActor = securityUtils.currentAuditActor(authentication);
        if (!StringUtils.hasText(auditActor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có thông tin người thực hiện để ghi nhận lịch sử");
        }
        return auditActor;
    }

    private void enforceCreateScope(DeaneryCreateRequest request, Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_ALL)) {
            return;
        }

        if (!securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền tạo giáo hạt");
        }

        String activeDioceseCode = getRequiredActiveDioceseCode(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE);
        if (!activeDioceseCode.equals(request.getDioceseCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Không thể tạo giáo hạt ngoài phạm vi giáo phận hiện tại");
        }
    }

    private void enforceDeleteScope(Authentication authentication, Deanery deanery) {
        if (securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_ALL)) {
            return;
        }

        if (!securityUtils.hasAuthority(authentication, DeaneryAccessEvaluator.DELETE_DIOCESE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xóa giáo hạt");
        }

        String activeDioceseCode = getRequiredActiveDioceseCode(authentication, DeaneryAccessEvaluator.DELETE_DIOCESE);
        String targetDioceseCode = deaneryRepository.findDioceseCodeByDeaneryCode(deanery.getDeaneryCode())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        errorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy giáo hạt: " + deanery.getDeaneryCode()
                ));

        if (!activeDioceseCode.equals(targetDioceseCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Không thể xóa giáo hạt ngoài phạm vi giáo phận hiện tại");
        }
    }

    private String getRequiredActiveDioceseCode(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + permissionCode);
        }

        return accountRepository.findActiveDioceseCodeByUsername(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        errorCode.ACCESS_DENIED,
                        "Tài khoản hiện tại không có phạm vi giáo phận hợp lệ cho quyền " + permissionCode
                ));
    }

    private String getRequiredActiveDeaneryCode(Authentication authentication, String permissionCode) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không xác định được tên đăng nhập cho quyền " + permissionCode);
        }

        return accountRepository.findActiveDeaneryCodeByUsername(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        errorCode.ACCESS_DENIED,
                        "Tài khoản hiện tại không có phạm vi giáo hạt hợp lệ cho quyền " + permissionCode
                ));
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private Page<DeaneryResponse> loadDeaneryPageWithDiocese(BaseSearchRequest request) {
        Page<Deanery> page = deaneryRepository.findAll(request);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<String> deaneryCodes = page.getContent().stream()
                .map(Deanery::getDeaneryCode)
                .toList();

        Map<String, Deanery> deaneryByCode = deaneryRepository.findAllByDeaneryCodesWithDiocese(deaneryCodes).stream()
                .collect(Collectors.toMap(Deanery::getDeaneryCode, Function.identity()));

        List<DeaneryResponse> hydratedContent = deaneryCodes.stream()
                .map(deaneryByCode::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toDeaneryResponse)
                .toList();

        return new PageImpl<>(hydratedContent, page.getPageable(), page.getTotalElements());
    }

    private DeaneryResponse toDeaneryResponse(Deanery deanery) {
        return DeaneryResponse.builder()
                .createdAt(deanery.getCreatedAt())
                .createdBy(deanery.getCreatedBy())
                .updatedAt(deanery.getUpdatedAt())
                .updatedBy(deanery.getUpdatedBy())
                .status(deanery.getStatus())
                .name(deanery.getName())
                .patronSaintDay(deanery.getPatronSaintDay())
                .establishmentDate(deanery.getEstablishmentDate())
                .chaplain(deanery.getChaplain())
                .phoneNumber(deanery.getPhoneNumber())
                .email(deanery.getEmail())
                .deaneryCode(deanery.getDeaneryCode())
                .associationName(deanery.getAssociationName())
                .dioceseCode(deanery.getDiocese() == null ? null : deanery.getDiocese().getDioceseCode())
                .build();
    }
}
