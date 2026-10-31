package phucuong.ht.common.utils;

import phucuong.ht.modules.leader.model.entity.Leader;

/** Cách ghép họ tên huynh trưởng dùng chung ("Họ Tên"; thiếu họ thì chỉ trả về tên). */
public final class LeaderNames {

    private LeaderNames() {
    }

    public static String fullName(Leader leader) {
        if (leader == null) {
            return null;
        }
        if (leader.getLastName() == null || leader.getLastName().isBlank()) {
            return leader.getFirstName();
        }
        return leader.getLastName() + " " + leader.getFirstName();
    }
}
