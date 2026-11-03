package phucuong.ht.modules.parish.repository;

/**
 * Vị trí của một đơn vị cơ sở để hiển thị trong thông báo: loại đơn vị (Giáo xứ / Giáo họ / Giáo điểm), tên, giáo hạt và giáo phận chứa nó.
 */
public record ParishPlace(String unitLabel, String name, String deaneryName, String dioceseName) {

    /** "Giáo xứ A (Giáo hạt B, Giáo phận C)": mỗi đơn vị đều kèm nhãn loại, không chỉ có tên. */
    public String describe() {
        return unitLabel + " " + name + " (Giáo hạt " + deaneryName + ", Giáo phận " + dioceseName + ")";
    }
}
