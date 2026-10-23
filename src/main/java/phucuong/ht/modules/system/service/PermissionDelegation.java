package phucuong.ht.modules.system.service;

import java.util.Collection;

/**
 * Quy tắc trao lại quyền theo cấp: người giữ quyền tạo khóa huấn luyện quá khứ cấp giáo phận được cấp quyền cùng loại cấp giáo hạt
 * cho admin giáo hạt của mình. Dùng cho trường hợp super-admin không xem được tài khoản admin giáo hạt nên không cấp trực tiếp được.
 */
final class PermissionDelegation {

    static final String CREATE_PAST_DIOCESE = "training.course.create_past.diocese";
    static final String CREATE_PAST_DEANERY = "training.course.create_past.deanery";

    private PermissionDelegation() {
    }

    /** true nếu người giữ {@code actorAuthorities} được trao lại quyền {@code permissionCode} dù không giữ đúng mã đó. */
    static boolean canDelegate(Collection<String> actorAuthorities, String permissionCode) {
        return CREATE_PAST_DEANERY.equals(permissionCode) && actorAuthorities.contains(CREATE_PAST_DIOCESE);
    }
}
