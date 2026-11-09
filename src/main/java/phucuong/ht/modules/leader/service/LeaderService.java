package phucuong.ht.modules.leader.service;

import phucuong.ht.config.EvictCaches;
import phucuong.ht.common.utils.LeaderNames;
import phucuong.ht.common.repository.SearchRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.config.CacheNames;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leader.helper.LeaderScopeHelper;
import phucuong.ht.modules.leader.helper.LeaderValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderSelfUpdateRequest;
import phucuong.ht.modules.leader.model.request.LeaderTransferRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leader.security.LeaderAccessEvaluator;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.service.MediaUrls;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.rankHistory.model.entity.RankHistory;
import phucuong.ht.modules.rankHistory.model.response.RankHistoryResponse;
import phucuong.ht.modules.rankHistory.repository.RankHistoryRepository;

import java.time.OffsetDateTime;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderService {

    private static final String DIOCESE_FILTER_FIELD = "parish.deanery.diocese.id";
    private static final String DEANERY_FILTER_FIELD = "parish.deanery.id";
    private static final String PARISH_FILTER_FIELD = "parish.id";

    private final phucuong.ht.modules.leaderLevel.service.LeaderLevelService leaderLevelService;
    private final LeaderRepository leaderRepository;
    private final LeaderValidationHelper leaderValidationHelper;
    private final LeaderScopeHelper leaderScopeHelper;
    private final AccountRepository accountRepository;
    private final RankHistoryRepository rankHistoryRepository;
    private final MediaAssetService mediaAssetService;
    private final MediaUrls mediaUrls;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    private final LeaderTransferNotifier leaderTransferNotifier;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.security.UnitOperationalStatusService unitOperationalStatusService;

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse create(LeaderCreateRequest request, Authentication authentication) {
        leaderValidationHelper.validateCreateInput(request);
        leaderScopeHelper.enforceCreateScope(request, authentication);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);

        try {
            Parish parish = leaderValidationHelper.requireExistingParish(request.getParishId());
            requireParishOperational(parish.getId());
            leaderValidationHelper.validateCreateEmailUniqueness(request.getEmail());
            leaderValidationHelper.validateCreatePhoneUniqueness(request.getPhoneNumber());
            LeaderValidationHelper.LeaderNameParts nameParts = leaderValidationHelper.splitFullName(request.getFullName());
            Leader leader = new Leader();
            leader.setHolyName(request.getHolyName());
            leader.setFirstName(nameParts.firstName());
            leader.setLastName(nameParts.lastName());
            leader.setBirthDate(request.getBirthDate());
            leader.setEmail(request.getEmail());
            leader.setPhoneNumber(request.getPhoneNumber());
            if (request.getImageAssetId() != null) {
                leader.setImageAssetId(mediaAssetService.attach(request.getImageAssetId(), MediaCategory.LEADER_AVATAR,
                        leaderValidationHelper.findDioceseIdOfParish(parish.getId()), auditActor));
            }
            leader.setGender(request.getGender());
            leader.setLeaderLevel(java.util.Objects.requireNonNullElse(leaderLevelService.requireExistingOrNull(request.getLeaderLevel(), "Cấp độ",
                    leaderValidationHelper.findDioceseIdOfParish(parish.getId())), "NONE"));
            leader.setParish(parish);
            leader.setStatus(Boolean.TRUE);
            OffsetDateTime now = OffsetDateTime.now();
            leader.setCreatedAt(now);
            leader.setUpdatedAt(now);
            leader.setCreatedBy(auditActor);
            leader.setUpdatedBy(auditActor);
            leaderRepository.save(leader);
            return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(leader.getId()));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu Huynh Trưởng bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    @Cacheable(cacheNames = CacheNames.LEADER_LIST, key = "@cacheScopeKeyHelper.listKey(#authentication, #request)")
    public Page<LeaderResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        if (!hasReadAuthority(authentication)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có quyền xem danh sách Huynh Trưởng");
        }

        BaseSearchRequest scopedRequest = SearchRequests.copy(request);
        if (hasAuthority(authentication, LeaderAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = leaderScopeHelper.getRequiredActiveDioceseId(authentication, LeaderAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return loadLeaderPageWithParish(scopedRequest);
        }

        if (hasAuthority(authentication, LeaderAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = leaderScopeHelper.getRequiredActiveDeaneryId(authentication, LeaderAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
            return loadLeaderPageWithParish(scopedRequest);
        }

        UUID parishId = leaderScopeHelper.getRequiredActiveParishId(authentication, LeaderAccessEvaluator.READ_PARISH);
        scopedRequest.getFilters().put(PARISH_FILTER_FIELD, parishId);
        return loadLeaderPageWithParish(scopedRequest);
    }

    public List<LeaderResponse> findAccountCandidates(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        // Tìm theo cụm từ: mỗi từ phải khớp (không phân biệt hoa thường / dấu) ở tên thánh, họ tên, email, SĐT, giáo xứ hoặc giáo hạt.
        // Làm ở đây vì cụm "Phêrô Nguyễn Tiến Đạt" trải trên nhiều cột nên tìm theo từng cột của BaseRepo không khớp được.
        List<String> tokens = searchTokens(source.getSearch());
        int targetSize = Math.max(source.getSize(), 100);
        List<LeaderResponse> candidates = new ArrayList<>();
        int pageIndex = Math.max(source.getPage(), 0);
        Page<LeaderResponse> page;
        do {
            BaseSearchRequest candidateRequest = SearchRequests.copy(source);
            candidateRequest.setSearch(null);
            candidateRequest.setSearchFields(List.of());
            candidateRequest.setPage(pageIndex++);
            candidateRequest.setSize(targetSize);
            page = findAll(candidateRequest, authentication);
            page.getContent().stream()
                    .filter(leader -> leader.getId() != null && matchesTokens(leader, tokens) && !accountRepository.existsByLeaderId(leader.getId()))
                    .forEach(candidates::add);
        } while (candidates.size() < targetSize && !page.isLast());
        return candidates.stream().limit(targetSize).toList();
    }

    private static List<String> searchTokens(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(foldText(search).split("\\s+")).filter(token -> !token.isEmpty()).toList();
    }

    private static boolean matchesTokens(LeaderResponse leader, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        String haystack = foldText(String.join(" ",
                Objects.toString(leader.getHolyName(), ""), Objects.toString(leader.getFullName(), ""),
                Objects.toString(leader.getEmail(), ""), Objects.toString(leader.getPhoneNumber(), ""),
                Objects.toString(leader.getParishName(), ""), Objects.toString(leader.getDeaneryName(), "")));
        return tokens.stream().allMatch(haystack::contains);
    }

    private static String foldText(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(java.util.Locale.ROOT);
    }

    @Cacheable(cacheNames = CacheNames.LEADER_DETAIL, key = "@cacheScopeKeyHelper.detailKey(#authentication, #id)")
    public LeaderResponse findById(UUID id, Authentication authentication) {
        return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
    }

    public List<RankHistoryResponse> findRankHistory(UUID id) {
        List<RankHistory> histories = rankHistoryRepository.findAllByLeaderId(id);
        Map<String, String> courseNames = rankHistoryRepository.findCourseNamesByCodes(histories.stream()
                .map(RankHistory::getCourseCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return histories.stream()
                .map(history -> toRankHistoryResponse(history, courseNames))
                .toList();
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    // Khóa / chứng nhận hiển thị tên huynh trưởng (người tạo, người kết thúc, người duyệt...) nên cũng phải xóa cache.
    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.ExecutiveAssignments
    public LeaderResponse update(UUID id, LeaderUpdateRequest request, Authentication authentication) {
        leaderValidationHelper.validateUpdateInput(request);
        Leader existingLeader = leaderValidationHelper.requireExistingLeaderWithParish(id);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);
        leaderValidationHelper.validateUpdateEmailUniqueness(id, request.getEmail(), existingLeader.getEmail());
        leaderValidationHelper.validateUpdatePhoneUniqueness(id, request.getPhoneNumber(), existingLeader.getPhoneNumber());

        UUID currentParishId = existingLeader.getParish() == null ? null : existingLeader.getParish().getId();
        // Đổi giáo xứ là chức năng riêng (quyền riêng) qua transfer(); sửa hồ sơ không được đổi giáo xứ.
        if (request.getParishId() != null && !request.getParishId().equals(currentParishId)) {
            throw badRequest("Muốn đổi giáo xứ của huynh trưởng, hãy dùng chức năng Chuyển giáo xứ");
        }
        // Đóng băng: không sửa hồ sơ trong đơn vị bị khóa.
        requireParishOperational(currentParishId);

        // Ảnh đại diện: gắn ảnh mới vào thư mục giáo phận (trước khi lưu hồ sơ) rồi mới đổi tham chiếu.
        UUID oldAssetId = existingLeader.getImageAssetId();
        UUID requestedAssetId = request.getImageAssetId();
        // Ảnh đại diện chỉ được thay bằng ảnh khác, không được xóa.
        if (request.getClearFields() != null
                && (request.getClearFields().contains("imageUrl") || request.getClearFields().contains("imageAssetId"))) {
            throw avatarCannotBeRemoved();
        }
        UUID newAssetId = null;
        if (requestedAssetId != null && !requestedAssetId.equals(oldAssetId)) {
            newAssetId = mediaAssetService.attach(requestedAssetId, MediaCategory.LEADER_AVATAR,
                    leaderValidationHelper.findDioceseIdOfParish(currentParishId), auditActor);
        }
        // Việc đổi tham chiếu ảnh làm riêng bên dưới, không để BaseRepo sao chép tự động.
        request.setImageAssetId(null);

        try {
            leaderRepository.update(id, request, auditActor);
            // Email không bắt buộc: clearFields chứa "email" thì xóa email (BaseRepo.update bỏ qua giá trị null).
            if (request.getClearFields() != null && request.getClearFields().contains("email") && request.getEmail() == null) {
                leaderRepository.clearEmail(id);
            }
            if (newAssetId != null) {
                leaderRepository.setImageAsset(id, newAssetId);
                mediaAssetService.release(oldAssetId);
            }
            // Tên hiển thị của người thao tác có cache trong bộ nhớ 60 giây: xóa để danh sách nạp lại dùng tên mới.
            auditActorNameResolver.evictAll();
            return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu Huynh Trưởng bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    /**
     * Chuyển huynh trưởng sang giáo xứ khác: chức năng riêng, cần quyền chuyển (giáo phận / giáo hạt) và giáo xứ đích phải nằm trong phạm vi của người thao tác.
     * Giáo xứ đích phải đang hoạt động. Xong thì báo cho tài khoản xem hồ sơ huynh trưởng của giáo xứ nguồn và đích.
     */
    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    @EvictCaches.Certificates
    @EvictCaches.ExecutiveAssignments
    public LeaderResponse transfer(UUID id, LeaderTransferRequest request, Authentication authentication) {
        Leader existingLeader = leaderValidationHelper.requireExistingLeaderWithParish(id);
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);
        Parish source = existingLeader.getParish();
        if (request.getParishId().equals(source == null ? null : source.getId())) {
            throw badRequest("Huynh trưởng đang thuộc giáo xứ này");
        }
        Parish target = leaderValidationHelper.requireExistingParish(request.getParishId());
        leaderScopeHelper.enforceTransferScope(authentication, target.getId());
        // Giáo xứ nguồn bị khóa vẫn được chuyển đi; giáo xứ đích phải đang hoạt động.
        requireParishOperational(target.getId());

        leaderRepository.setParish(id, target.getId(), auditActor);
        announceTransfer(existingLeader, target, auditActor);
        return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
    }

    /**
     * Huynh trưởng tự sửa thông tin cá nhân của chính mình (không gồm giáo xứ, giáo hạt, cấp độ).
     * Email / số điện thoại chỉ kiểm tra trùng khi giá trị thật sự đổi. Đơn vị bị khóa thì không sửa được, như khi quản trị sửa hồ sơ.
     */
    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    // Khóa / chứng nhận hiển thị tên huynh trưởng (người tạo, người kết thúc, người duyệt...) nên cũng phải xóa cache.
    @EvictCaches.Courses
    @EvictCaches.Certificates
    @EvictCaches.ExecutiveAssignments
    public LeaderResponse updateOwnProfile(UUID id, LeaderSelfUpdateRequest request, String auditActor) {
        Leader existing = leaderValidationHelper.requireExistingLeaderWithParish(id);
        UUID parishId = existing.getParish() == null ? null : existing.getParish().getId();
        requireParishOperational(parishId);

        request.setHolyName(requiredIfPresent(request.getHolyName(), "Tên thánh"));
        request.setEmail(requiredIfPresent(request.getEmail(), "Email"));
        request.setPhoneNumber(requiredIfPresent(request.getPhoneNumber(), "Số điện thoại"));
        if (request.getFullName() != null) {
            LeaderValidationHelper.LeaderNameParts parts = leaderValidationHelper.splitFullName(request.getFullName());
            request.setFirstName(parts.firstName());
            request.setLastName(parts.lastName());
        }
        // Chỉ kiểm tra trùng khi giá trị đổi so với hiện tại.
        leaderValidationHelper.validateUpdateEmailUniqueness(id, request.getEmail(), existing.getEmail());
        leaderValidationHelper.validateUpdatePhoneUniqueness(id, request.getPhoneNumber(), existing.getPhoneNumber());

        UUID oldAssetId = existing.getImageAssetId();
        UUID requestedAssetId = request.getImageAssetId();
        if (Boolean.TRUE.equals(request.getClearImage())) {
            throw avatarCannotBeRemoved();
        }
        UUID newAssetId = null;
        if (requestedAssetId != null && !requestedAssetId.equals(oldAssetId)) {
            newAssetId = mediaAssetService.attach(requestedAssetId, MediaCategory.LEADER_AVATAR,
                    leaderValidationHelper.findDioceseIdOfParish(parishId), auditActor);
        }
        // Việc đổi tham chiếu ảnh làm riêng bên dưới, không để BaseRepo sao chép tự động.
        request.setImageAssetId(null);

        try {
            leaderRepository.update(id, request, auditActor);
            if (newAssetId != null) {
                leaderRepository.setImageAsset(id, newAssetId);
                mediaAssetService.release(oldAssetId);
            }
            // Tên hiển thị của người thao tác có cache trong bộ nhớ 60 giây: xóa để danh sách nạp lại dùng tên mới.
            auditActorNameResolver.evictAll();
            return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Email hoặc số điện thoại đã được dùng bởi hồ sơ khác");
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }

    private ApiException avatarCannotBeRemoved() {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Không thể xóa ảnh đại diện, chỉ có thể thay bằng ảnh khác");
    }

    // Trường có gửi lên (khác null) thì không được rỗng; trả về giá trị đã cắt khoảng trắng.
    private String requiredIfPresent(String value, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, label + " không được để trống");
        }
        return trimmed;
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public LeaderResponse toggleStatus(UUID id, StatusToggleRequest request, Authentication authentication) {
        leaderValidationHelper.requireExistingLeaderWithParish(id);
        leaderValidationHelper.validateToggleConfirmation(request == null ? null : request.getConfirmationText());
        String auditActor = leaderValidationHelper.getRequiredAuditActor(authentication);
        leaderRepository.toggleStatus(id, auditActor);
        return toLeaderResponse(leaderValidationHelper.requireExistingLeaderWithParish(id));
    }

    // Thông báo chuyển giáo xứ chỉ là phụ: lỗi gửi thông báo không làm hỏng việc chuyển đã lưu.
    private void announceTransfer(Leader leader, Parish target, String actor) {
        try {
            leaderTransferNotifier.announce(leader, leader.getParish(), target, actor);
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(LeaderService.class).warn("Không gửi được thông báo chuyển giáo xứ của huynh trưởng {}", leader.getId(), exception);
        }
    }

    private void requireParishOperational(UUID parishId) {
        if (unitOperationalStatusService != null && parishId != null) {
            unitOperationalStatusService.requireUnitOperational(phucuong.ht.common.enums.UnitType.PARISH, parishId);
        }
    }

    private boolean hasReadAuthority(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(authority -> LeaderAccessEvaluator.READ_DIOCESE.equals(authority)
                        || LeaderAccessEvaluator.READ_DEANERY.equals(authority)
                        || LeaderAccessEvaluator.READ_PARISH.equals(authority));
    }

    private boolean hasAuthority(Authentication authentication, String permissionCode) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(permissionCode::equals);
    }

    private Page<LeaderResponse> loadLeaderPageWithParish(BaseSearchRequest request) {
        Page<Leader> page = leaderRepository.findAll(request);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<UUID> ids = page.getContent().stream()
                .map(Leader::getId)
                .toList();

        Map<UUID, Leader> leaderById = leaderRepository.findAllByIdsWithParish(ids).stream()
                .collect(Collectors.toMap(Leader::getId, Function.identity()));

        List<LeaderResponse> hydratedContent = ids.stream()
                .map(leaderById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toLeaderResponse)
                .toList();

        return new PageImpl<>(hydratedContent, page.getPageable(), page.getTotalElements());
    }

    private LeaderResponse toLeaderResponse(Leader leader) {
        return LeaderResponse.builder()
                .id(leader.getId())
                .createdAt(leader.getCreatedAt())
                .createdBy(leader.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(leader.getCreatedBy()))
                .updatedAt(leader.getUpdatedAt())
                .updatedBy(leader.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(leader.getUpdatedBy()))
                .status(leader.getStatus())
                .holyName(leader.getHolyName())
                .fullName(LeaderNames.fullName(leader))
                .firstName(leader.getFirstName())
                .lastName(leader.getLastName())
                .birthDate(leader.getBirthDate())
                .email(leader.getEmail())
                .phoneNumber(leader.getPhoneNumber())
                .imageUrl(mediaUrls.displayOrLegacy(leader.getImageAssetId(), leader.getLegacyImageData(), MediaCategory.LEADER_AVATAR))
                .imageThumbUrl(mediaUrls.smallOrLegacy(leader.getImageAssetId(), leader.getLegacyImageData(), MediaCategory.LEADER_AVATAR))
                .imageAssetId(leader.getImageAssetId())
                .gender(leader.getGender())
                .leaderLevel(leader.getLeaderLevel())
                .deaneryId(leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getId())
                .deaneryName(leader.getParish() == null || leader.getParish().getDeanery() == null ? null : leader.getParish().getDeanery().getName())
                .parishId(leader.getParish() == null ? null : leader.getParish().getId())
                .parishName(leader.getParish() == null ? null : leader.getParish().getName())
                .subUnitName(leader.getParish() == null ? null : phucuong.ht.modules.parish.model.ParishUnitNames.of(leader.getParish()).subUnitName())
                .build();
    }

    private RankHistoryResponse toRankHistoryResponse(RankHistory rankHistory, Map<String, String> courseNames) {
        String courseCode = rankHistory.getCourseCode();
        String courseName = courseCode == null ? null : courseNames.get(courseCode);
        String note = rankHistory.getNote();
        // Ghi chú lưu mã khóa (UUID với khóa quá khứ): đổi sang tên khóa để hiển thị.
        if (note != null && courseCode != null && courseName != null) {
            note = note.replace(courseCode, courseName);
        }
        return RankHistoryResponse.builder()
                .id(rankHistory.getId())
                .oldLevel(rankHistory.getOldLevel())
                .newLevel(rankHistory.getNewLevel())
                .promotionDate(rankHistory.getPromotionDate())
                .note(note)
                .courseCode(courseCode)
                .courseName(courseName)
                .createdAt(rankHistory.getCreatedAt())
                .createdBy(rankHistory.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(rankHistory.getCreatedBy()))
                .updatedAt(rankHistory.getUpdatedAt())
                .updatedBy(rankHistory.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(rankHistory.getUpdatedBy()))
                .build();
    }

}
