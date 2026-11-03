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

    /** Tên hiển thị trong thông báo/thông điệp: tên thánh đứng đầu rồi tới họ tên ("Giuse Nguyễn Văn A"); thiếu tên thánh thì chỉ họ tên. */
    public static String displayName(Leader leader) {
        String fullName = fullName(leader);
        if (leader == null || leader.getHolyName() == null || leader.getHolyName().isBlank()) {
            return fullName;
        }
        return fullName == null ? leader.getHolyName().trim() : leader.getHolyName().trim() + " " + fullName;
    }
}
