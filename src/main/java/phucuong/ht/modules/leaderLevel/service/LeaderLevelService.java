package phucuong.ht.modules.leaderLevel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leaderLevel.model.entity.LeaderLevel;
import phucuong.ht.modules.leaderLevel.model.request.LeaderLevelReorderRequest;
import phucuong.ht.modules.leaderLevel.model.request.LeaderLevelRequest;
import phucuong.ht.modules.leaderLevel.model.response.LeaderLevelResponse;
import phucuong.ht.modules.leaderLevel.repository.LeaderLevelRepository;
import phucuong.ht.modules.leaderLevel.repository.LeaderLevelRepository.Usage;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

// Danh mục cấp độ huynh trưởng của từng giáo phận và các phép so sánh thứ tự cấp.
// Mỗi giáo phận có danh mục riêng; mã cấp (ví dụ HT_I) là giá trị lưu ở dữ liệu, tên và thứ tự tra theo giáo phận.
@Service
@RequiredArgsConstructor
public class LeaderLevelService {

    public static final String NONE = "NONE";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    // Danh mục mặc định của giáo phận mới: mã, tên, cấp hệ thống.
    private static final List<String[]> DEFAULT_LEVELS = List.of(
            new String[]{"NONE", "Chưa có cấp", "true"},
            new String[]{"HT_XU", "Huynh trưởng xứ", "false"},
            new String[]{"DU_TRUONG", "Dự trưởng", "false"},
            new String[]{"HT_I", "Huynh trưởng cấp I", "false"},
            new String[]{"HT_II", "Huynh trưởng cấp II", "false"},
            new String[]{"HT_III", "Huynh trưởng cấp III", "false"},
            new String[]{"HLV_I", "Huấn luyện viên cấp I", "false"},
            new String[]{"HLV_II", "Huấn luyện viên cấp II", "false"},
            new String[]{"HLV_III", "Huấn luyện viên cấp III", "false"});

    private final LeaderLevelRepository repository;
    private final CommonValidationHelper validationHelper;
    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    // Thứ tự các cấp của một giáo phận tại một thời điểm, dùng để so sánh lớn/nhỏ mà không phải truy vấn lặp lại.
    public record LevelOrder(Map<String, Integer> orders, Map<String, String> names) {
        // -1 nếu mã không có trong danh mục.
        public int rank(String code) {
            Integer order = code == null ? null : orders.get(code);
            return order == null ? -1 : order;
        }

        // a cao hơn b. Mã null coi như thấp hơn mọi cấp.
        public boolean higher(String a, String b) {
            return rank(a) > rank(b);
        }

        public boolean atMost(String a, String b) {
            return rank(a) <= rank(b);
        }

        public String nameOf(String code) {
            return code == null ? "" : names.getOrDefault(code, code);
        }
    }

    // Giáo phận đang hoạt động của tài khoản (tài khoản giáo phận / giáo hạt / giáo xứ / huynh trưởng đều có).
    @Transactional(readOnly = true)
    public Optional<UUID> activeDioceseId(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        return username == null || username.isBlank() ? Optional.empty() : accountRepository.findActiveDioceseIdByUsername(username);
    }

    private UUID requireActorDiocese(Authentication authentication) {
        return activeDioceseId(authentication)
                .orElseThrow(() -> validationHelper.forbidden("Tài khoản chưa thuộc giáo phận nào đang hoạt động"));
    }

    // Thứ tự cấp của giáo phận; giáo phận không xác định (null) thì không có cấp nào.
    @Transactional(readOnly = true)
    public LevelOrder order(UUID dioceseId) {
        Map<String, Integer> orders = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        if (dioceseId != null) {
            for (LeaderLevel level : repository.findAllOrdered(dioceseId)) {
                orders.put(level.getLevelCode(), level.getDisplayOrder());
                names.put(level.getLevelCode(), level.getLevelName());
            }
        }
        return new LevelOrder(orders, names);
    }

    // Cấp đứng ngay trước trong thứ tự (điều kiện để đạt cấp này).
    @Transactional(readOnly = true)
    public Optional<String> previousOf(UUID dioceseId, String code) {
        String previous = null;
        for (LeaderLevel level : repository.findAllOrdered(dioceseId)) {
            if (level.getLevelCode().equals(code)) {
                return Optional.ofNullable(previous);
            }
            previous = level.getLevelCode();
        }
        return Optional.empty();
    }

    // Mã cấp độ phải có trong danh mục của giáo phận; trả về mã đã chuẩn hóa (hoặc null nếu để trống).
    // dioceseId = null (không xác định được giáo phận, ví dụ super admin) thì chỉ chuẩn hóa, không đối chiếu.
    @Transactional(readOnly = true)
    public String requireExistingOrNull(String code, String label, UUID dioceseId) {
        String normalized = validationHelper.normalizeOptionalText(code);
        if (normalized == null || dioceseId == null) {
            return normalized;
        }
        if (repository.findByCode(dioceseId, normalized).isEmpty()) {
            throw validationHelper.validationError(label + " không tồn tại trong danh mục cấp độ của giáo phận: " + normalized);
        }
        return normalized;
    }

    // Cấp độ đạt được của hồ sơ ngoại lệ: phải có trong danh mục, cao hơn cấp hiện tại của huynh trưởng
    // và không cao hơn cấp độ đạt được của loại khóa. Trả về mã cấp đã chuẩn hóa.
    @Transactional(readOnly = true)
    public String requireAchievable(String requestedLevel, String currentLevel, String courseLevel, UUID dioceseId) {
        String level = validationHelper.normalizeOptionalText(requestedLevel);
        if (level == null) {
            throw validationHelper.validationError("Phải chọn cấp độ đạt được khi duyệt hồ sơ ngoại lệ");
        }
        LevelOrder levelOrder = order(dioceseId);
        if (levelOrder.rank(level) < 0) {
            throw validationHelper.validationError("Cấp độ đạt được không tồn tại: " + level);
        }
        if (!levelOrder.higher(level, currentLevel)) {
            throw validationHelper.validationError("Cấp độ đạt được (" + levelOrder.nameOf(level) + ") phải cao hơn cấp hiện tại ("
                    + levelOrder.nameOf(currentLevel) + ")");
        }
        if (!levelOrder.atMost(level, courseLevel)) {
            throw validationHelper.validationError("Cấp độ đạt được (" + levelOrder.nameOf(level) + ") không được cao hơn cấp độ đạt được của khóa ("
                    + levelOrder.nameOf(courseLevel) + ")");
        }
        return level;
    }

    // Tạo danh mục mặc định cho giáo phận chưa có cấp nào (giáo phận mới). Không làm gì nếu đã có.
    @Transactional
    public void seedDefaults(UUID dioceseId, String actor) {
        if (dioceseId == null || repository.hasAny(dioceseId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        int order = 0;
        for (String[] def : DEFAULT_LEVELS) {
            LeaderLevel level = new LeaderLevel();
            level.setDioceseId(dioceseId);
            level.setLevelCode(def[0]);
            level.setLevelName(def[1]);
            level.setIsSystem(Boolean.parseBoolean(def[2]));
            level.setDisplayOrder(order++);
            level.setStatus(Boolean.TRUE);
            level.setCreatedAt(now);
            level.setCreatedBy(actor);
            level.setUpdatedAt(now);
            level.setUpdatedBy(actor);
            repository.persist(level);
        }
    }

    // Danh mục của giáo phận người dùng (gọn). Tài khoản không thuộc giáo phận nào chỉ cần nhãn: gộp theo mã.
    @Transactional
    public List<LeaderLevelResponse> options(Authentication authentication) {
        Optional<UUID> dioceseId = activeDioceseId(authentication);
        if (dioceseId.isPresent()) {
            seedDefaults(dioceseId.get(), securityUtils.currentAuditActor(authentication));
            return toResponses(repository.findAllOrdered(dioceseId.get()), null);
        }
        Map<String, LeaderLevel> byCode = new LinkedHashMap<>();
        repository.findAllAcrossDioceses().forEach(level -> byCode.putIfAbsent(level.getLevelCode(), level));
        return toResponses(new ArrayList<>(byCode.values()), null);
    }

    @Transactional
    public List<LeaderLevelResponse> list(Authentication authentication) {
        UUID dioceseId = requireActorDiocese(authentication);
        seedDefaults(dioceseId, securityUtils.currentAuditActor(authentication));
        return listFor(dioceseId);
    }

    @Transactional
    public LeaderLevelResponse create(LeaderLevelRequest request, Authentication authentication) {
        UUID dioceseId = requireActorDiocese(authentication);
        String name = validationHelper.normalizeRequiredText(request.getLevelName());
        if (name == null || name.isBlank()) {
            throw validationHelper.validationError("Tên cấp độ là bắt buộc");
        }
        String code = validationHelper.normalizeOptionalText(request.getLevelCode());
        code = code == null ? codeFromName(name) : code.toUpperCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw validationHelper.validationError("Mã cấp độ chỉ gồm chữ hoa, số, gạch dưới và bắt đầu bằng chữ (2-50 ký tự)");
        }
        if (repository.findByCode(dioceseId, code).isPresent()) {
            throw validationHelper.conflict("Mã cấp độ đã tồn tại: " + code);
        }
        requireUniqueName(dioceseId, name, null);

        String actor = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();
        LeaderLevel level = new LeaderLevel();
        level.setDioceseId(dioceseId);
        level.setLevelCode(code);
        level.setLevelName(name);
        level.setDescription(validationHelper.normalizeOptionalText(request.getDescription()));
        level.setIsSystem(Boolean.FALSE);
        // Cấp mới nằm trên cùng; kéo thả để đổi vị trí.
        level.setDisplayOrder(repository.maxOrder(dioceseId) + 1);
        level.setStatus(Boolean.TRUE);
        level.setCreatedAt(now);
        level.setCreatedBy(actor);
        level.setUpdatedAt(now);
        level.setUpdatedBy(actor);
        repository.persist(level);
        return listOne(dioceseId, code);
    }

    @Transactional
    public LeaderLevelResponse update(String code, LeaderLevelRequest request, Authentication authentication) {
        UUID dioceseId = requireActorDiocese(authentication);
        LeaderLevel level = requireLevel(dioceseId, code);
        String name = validationHelper.normalizeRequiredText(request.getLevelName());
        if (name == null || name.isBlank()) {
            throw validationHelper.validationError("Tên cấp độ là bắt buộc");
        }
        requireUniqueName(dioceseId, name, code);
        level.setLevelName(name);
        level.setDescription(validationHelper.normalizeOptionalText(request.getDescription()));
        level.setUpdatedAt(OffsetDateTime.now());
        level.setUpdatedBy(securityUtils.currentAuditActor(authentication));
        repository.merge(level);
        return listOne(dioceseId, code);
    }

    // Sắp xếp lại thứ tự: danh sách phải gồm đủ và đúng các cấp không phải cấp hệ thống, từ thấp đến cao.
    @Transactional
    public List<LeaderLevelResponse> reorder(LeaderLevelReorderRequest request, Authentication authentication) {
        UUID dioceseId = requireActorDiocese(authentication);
        List<LeaderLevel> all = repository.findAllOrdered(dioceseId);
        List<LeaderLevel> movable = all.stream().filter(level -> !Boolean.TRUE.equals(level.getIsSystem())).toList();
        List<String> requested = request.getLevelCodes();
        Set<String> expected = new HashSet<>();
        movable.forEach(level -> expected.add(level.getLevelCode()));
        if (requested.size() != expected.size() || !expected.equals(new HashSet<>(requested))) {
            throw validationHelper.validationError("Danh sách sắp xếp phải gồm đủ và đúng các cấp độ hiện có (không tính cấp hệ thống)");
        }
        String actor = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();
        // Cấp hệ thống giữ thứ tự 0, các cấp còn lại đánh số 1..n. Ràng buộc duy nhất kiểm tra khi commit.
        int next = 1;
        Map<String, LeaderLevel> byCode = new LinkedHashMap<>();
        movable.forEach(level -> byCode.put(level.getLevelCode(), level));
        for (String code : requested) {
            LeaderLevel level = byCode.get(code);
            if (level.getDisplayOrder() != next) {
                level.setDisplayOrder(next);
                level.setUpdatedAt(now);
                level.setUpdatedBy(actor);
                repository.merge(level);
            }
            next++;
        }
        return listFor(dioceseId);
    }

    @Transactional
    public void delete(String code, Authentication authentication) {
        UUID dioceseId = requireActorDiocese(authentication);
        LeaderLevel level = requireLevel(dioceseId, code);
        if (Boolean.TRUE.equals(level.getIsSystem())) {
            throw validationHelper.conflict("Không thể xóa cấp hệ thống");
        }
        Usage usage = repository.usageByCode(dioceseId).getOrDefault(code, new Usage(0, 0, 0, 0));
        if (usage.inUse()) {
            throw validationHelper.conflict("Cấp độ đang được sử dụng (" + usage.leaders() + " huynh trưởng, " + usage.courseTypes()
                    + " loại khóa, " + usage.rankHistories() + " lịch sử cấp bậc, " + usage.participations()
                    + " hồ sơ tham dự) nên không thể xóa");
        }
        repository.remove(dioceseId, code);
    }

    private LeaderLevel requireLevel(UUID dioceseId, String code) {
        return repository.findByCode(dioceseId, code).orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấp độ: " + code));
    }

    private void requireUniqueName(UUID dioceseId, String name, String exceptCode) {
        boolean duplicated = repository.findAllOrdered(dioceseId).stream()
                .anyMatch(level -> !level.getLevelCode().equals(exceptCode) && level.getLevelName().equalsIgnoreCase(name));
        if (duplicated) {
            throw validationHelper.conflict("Tên cấp độ đã tồn tại: " + name);
        }
    }

    private List<LeaderLevelResponse> listFor(UUID dioceseId) {
        return toResponses(repository.findAllOrdered(dioceseId), repository.usageByCode(dioceseId));
    }

    private LeaderLevelResponse listOne(UUID dioceseId, String code) {
        return listFor(dioceseId).stream().filter(item -> item.getLevelCode().equals(code)).findFirst()
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấp độ: " + code));
    }

    // Sinh mã từ tên: bỏ dấu, chữ hoa, khoảng trắng thành gạch dưới.
    static String codeFromName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private List<LeaderLevelResponse> toResponses(List<LeaderLevel> levels, Map<String, Usage> usage) {
        List<LeaderLevelResponse> result = new ArrayList<>();
        LeaderLevel previous = null;
        for (LeaderLevel level : levels) {
            Usage used = usage == null ? null : usage.getOrDefault(level.getLevelCode(), new Usage(0, 0, 0, 0));
            result.add(LeaderLevelResponse.builder()
                    .levelCode(level.getLevelCode())
                    .levelName(level.getLevelName())
                    .displayOrder(level.getDisplayOrder())
                    .isSystem(level.getIsSystem())
                    .description(level.getDescription())
                    .previousCode(previous == null ? null : previous.getLevelCode())
                    .previousName(previous == null ? null : previous.getLevelName())
                    .leaderCount(used == null ? null : used.leaders())
                    .courseTypeCount(used == null ? null : used.courseTypes())
                    .deletable(used == null ? null : !Boolean.TRUE.equals(level.getIsSystem()) && !used.inUse())
                    .build());
            previous = level;
        }
        return result;
    }
}
