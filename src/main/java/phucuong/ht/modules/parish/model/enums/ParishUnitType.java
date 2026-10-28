package phucuong.ht.modules.parish.model.enums;

/** Loại đơn vị cơ sở: giáo xứ, hoặc giáo họ / giáo điểm thuộc một giáo xứ. */
public enum ParishUnitType {
    PARISH("Giáo xứ"),
    GIAO_HO("Giáo họ"),
    GIAO_DIEM("Giáo điểm");

    private final String label;

    ParishUnitType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
