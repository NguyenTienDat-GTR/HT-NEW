package phucuong.ht.modules.parish.repository;

/**
 * Vị trí của một đơn vị cơ sở để hiển thị trong thông báo: loại đơn vị (Giáo xứ / Giáo họ / Giáo điểm), tên, giáo hạt và giáo phận chứa nó.
 */
public record ParishPlace(String unitLabel, String name, String deaneryName, String dioceseName) {

    /** "Giáo xứ A (Giáo hạt B, Giáo phận C)": mỗi đơn vị đều kèm nhãn loại, không chỉ có tên. */
    public String describe() {
        return withLabel(unitLabel, name) + " (" + withLabel("Giáo hạt", deaneryName) + ", " + withLabel("Giáo phận", dioceseName) + ")";
    }

    // Tên trong dữ liệu có thể đã mang sẵn nhãn ("Giáo xứ Phú Hòa", "Giáo hạt Thủ Dầu Một"): chỉ thêm nhãn khi tên chưa có.
    private static String withLabel(String label, String value) {
        String text = value == null ? "" : value.trim();
        return text.toLowerCase().startsWith(label.toLowerCase()) ? text : label + " " + text;
    }
}
