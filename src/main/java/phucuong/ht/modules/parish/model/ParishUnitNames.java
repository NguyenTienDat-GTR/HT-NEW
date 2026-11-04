package phucuong.ht.modules.parish.model;

import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;

/**
 * Tên đơn vị cơ sở dùng cho các trường trộn khi in chứng nhận / thẻ.
 * Giáo họ và giáo điểm luôn thuộc một giáo xứ mẹ, nên nhãn "Giáo xứ" in sẵn trên ảnh nền vẫn đúng.
 *
 * @param parishName  tên giáo xứ (giáo xứ mẹ nếu đơn vị là giáo họ / giáo điểm)
 * @param subUnitName "Giáo họ X" / "Giáo điểm Y"; rỗng nếu đơn vị là giáo xứ
 * @param fullName    "Giáo họ X – Giáo xứ Z" hoặc "Giáo xứ Z"
 */
public record ParishUnitNames(String parishName, String subUnitName, String fullName) {

    private static final String SEPARATOR = " – ";

    public static ParishUnitNames of(Parish parish) {
        if (parish == null) {
            return new ParishUnitNames("", "", "");
        }
        String own = trim(parish.getName());
        boolean subUnit = parish.getUnitType() != null && parish.getUnitType() != ParishUnitType.PARISH;
        Parish parent = subUnit ? parish.getParentParish() : null;
        if (!subUnit || parent == null) {
            // Giáo xứ, hoặc dữ liệu thiếu giáo xứ cha: giữ tên đơn vị như cũ.
            String label = subUnit ? withLabel(parish.getUnitType().getLabel(), own) : "";
            return new ParishUnitNames(own, label, subUnit ? label : own);
        }
        String parishName = trim(parent.getName());
        String sub = withLabel(parish.getUnitType().getLabel(), own);
        return new ParishUnitNames(parishName, sub, sub + SEPARATOR + withLabel(ParishUnitType.PARISH.getLabel(), parishName));
    }

    /** Chỉ thêm nhãn loại khi tên chưa mang sẵn nhãn ("Giáo họ Bà Lụa" không thành "Giáo họ Giáo họ Bà Lụa"). */
    public static String withLabel(String label, String value) {
        String text = trim(value);
        if (text.isEmpty()) {
            return "";
        }
        return text.toLowerCase().startsWith(label.toLowerCase()) ? text : label + " " + text;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
