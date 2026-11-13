package phucuong.ht.modules.training.courseType.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.security.AuthorityChecks;
import phucuong.ht.common.utils.RequestMemo;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ai thấy và ai sửa được loại khóa:
 * <ul>
 *   <li>Loại khóa của giáo phận (cấp giáo phận, hoặc cấp giáo hạt dùng chung): mọi giáo hạt trong giáo phận thấy và dùng được,
 *       chỉ tài khoản giáo phận sửa / đổi trạng thái được.</li>
 *   <li>Loại khóa giáo hạt tự tạo: chỉ giáo hạt đó thấy, dùng và sửa; giáo phận không thấy trong danh mục của mình.</li>
 *   <li>Tài khoản không thuộc giáo phận nào (super admin) không bị giới hạn.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CourseTypeOwnership {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper validation;

    /** Chủ sở hữu của loại khóa mới: deaneryId chỉ có khi unit = DEANERY. */
    public record Owner(UnitType unit, UUID deaneryId) {
        public static final Owner DIOCESE = new Owner(UnitType.DIOCESE, null);
    }

    private Optional<String> username(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        return username == null || username.isBlank() ? Optional.empty() : Optional.of(username);
    }

    // Giáo hạt của tài khoản gắn trực tiếp hoặc suy ra từ giáo xứ / huynh trưởng.
    public Optional<UUID> activeDeaneryId(Authentication authentication) {
        return username(authentication).flatMap(accountRepository::findActiveDeaneryIdByUsername);
    }

    /**
     * Phạm vi của tài khoản, tra một lần cho cả request (tránh truy vấn lặp theo từng dòng khi dựng danh sách).
     * dioceseLevel: tài khoản có quyền loại khóa cấp giáo phận. ownDeaneryId: giáo hạt của tài khoản cấp giáo hạt (null nếu là cấp giáo phận
     * hoặc không có quyền cấp giáo hạt). Cấp lấy từ quyền, đơn vị suy ra từ huynh trưởng / giáo xứ: không dựa vào việc tài khoản gắn trực tiếp.
     */
    public record Scope(boolean unscoped, boolean dioceseLevel, UUID ownDeaneryId, UUID activeDeaneryId) {
    }

    // Một request hỏi phạm vi nhiều lần (kiểm tra quyền ở controller, kiểm tra chủ sở hữu, dựng dữ liệu trả về): chỉ tra DB một lần.
    // Ghi nhớ cả ở request ghi vì thao tác với loại khóa / khóa / cấu hình chứng nhận không đổi đơn vị của tài khoản.
    public Scope scope(Authentication authentication) {
        return username(authentication)
                .map(name -> RequestMemo.getInAnyRequest("courseTypeScope:" + name, () -> loadScope(authentication)))
                .orElseGet(() -> loadScope(authentication));
    }

    private Scope loadScope(Authentication authentication) {
        boolean unscoped = username(authentication).flatMap(accountRepository::findActiveDioceseIdByUsername).isEmpty();
        boolean dioceseLevel = AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.CREATE_DIOCESE)
                || AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.READ_DIOCESE)
                || AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.UPDATE_DIOCESE)
                || AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.TOGGLE_DIOCESE);
        boolean deaneryLevel = !dioceseLevel && (AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.CREATE_DEANERY)
                || AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.READ_DEANERY)
                || AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.UPDATE_DEANERY)
                || AuthorityChecks.hasAuthority(authentication, TrainingCourseTypeAccessEvaluator.TOGGLE_DEANERY));
        Optional<UUID> activeDeanery = activeDeaneryId(authentication);
        return new Scope(unscoped, dioceseLevel, deaneryLevel ? activeDeanery.orElse(null) : null, activeDeanery.orElse(null));
    }

    // Loại khóa cấp giáo hạt tạo bởi tài khoản giáo hạt là của giáo hạt đó; tạo bởi tài khoản giáo phận là loại dùng chung.
    public Owner ownerForNewType(UnitType managementUnitType, Authentication authentication) {
        if (managementUnitType != UnitType.DEANERY) {
            return Owner.DIOCESE;
        }
        Scope scope = scope(authentication);
        if (scope.ownDeaneryId() != null) {
            return new Owner(UnitType.DEANERY, scope.ownDeaneryId());
        }
        if (scope.dioceseLevel()) {
            return Owner.DIOCESE;
        }
        throw validation.forbidden("Chỉ tài khoản giáo phận hoặc giáo hạt mới tạo được loại khóa cấp giáo hạt");
    }

    public static boolean ownedByDeanery(TrainingCourseType type) {
        return type.getOwnerUnitType() == UnitType.DEANERY;
    }

    public boolean canSee(TrainingCourseType type, Scope scope) {
        if (!ownedByDeanery(type) || scope.unscoped()) {
            return true;
        }
        return !scope.dioceseLevel() && scope.activeDeaneryId() != null && scope.activeDeaneryId().equals(type.getOwnerDeaneryId());
    }

    public boolean canManage(TrainingCourseType type, Scope scope) {
        if (scope.unscoped()) {
            return true;
        }
        if (ownedByDeanery(type)) {
            return scope.ownDeaneryId() != null && scope.ownDeaneryId().equals(type.getOwnerDeaneryId());
        }
        return scope.dioceseLevel();
    }

    public boolean canSee(TrainingCourseType type, Authentication authentication) {
        return canSee(type, scope(authentication));
    }

    public boolean canManage(TrainingCourseType type, Authentication authentication) {
        return canManage(type, scope(authentication));
    }

    public void requireVisible(TrainingCourseType type, Authentication authentication) {
        if (!canSee(type, authentication)) {
            throw validation.forbidden("Loại khóa huấn luyện thuộc giáo hạt khác, bạn không có quyền xem");
        }
    }

    public void requireManageable(TrainingCourseType type, Authentication authentication) {
        if (!canManage(type, authentication)) {
            throw validation.forbidden(ownedByDeanery(type)
                    ? "Loại khóa này do giáo hạt khác tự tạo, bạn không có quyền thao tác"
                    : "Loại khóa này do giáo phận quản lý, giáo hạt chỉ được dùng, không được sửa hoặc đổi trạng thái");
        }
    }

    // Thu hẹp danh sách theo loại khóa tài khoản được thấy: tài khoản giáo phận không thấy loại giáo hạt tự tạo;
    // tài khoản giáo hạt thấy loại của giáo phận và loại do chính giáo hạt mình tạo.
    public BaseSearchRequest scopeList(BaseSearchRequest request, Authentication authentication) {
        Scope scope = scope(authentication);
        if (scope.unscoped()) {
            return request;
        }
        List<Map<String, Object>> groups = new java.util.ArrayList<>();
        groups.add(group(UnitType.DIOCESE, null));
        if (!scope.dioceseLevel() && scope.activeDeaneryId() != null) {
            groups.add(group(UnitType.DEANERY, scope.activeDeaneryId()));
        }
        request.setScopeAnyOf(groups);
        return request;
    }

    private static Map<String, Object> group(UnitType owner, UUID deaneryId) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("ownerUnitType", owner);
        if (deaneryId != null) {
            group.put("ownerDeaneryId", deaneryId);
        }
        return group;
    }
}
