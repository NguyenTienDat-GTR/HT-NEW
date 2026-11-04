package phucuong.ht.modules.parish.model;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParishUnitNamesTest {

    private static Parish unit(String name, ParishUnitType type, Parish parent) {
        Parish p = new Parish();
        p.setName(name);
        p.setUnitType(type);
        p.setParentParish(parent);
        return p;
    }

    @Test
    void parishKeepsOwnNameAndHasNoSubUnit() {
        ParishUnitNames n = ParishUnitNames.of(unit("Giáo xứ Phú Cường", ParishUnitType.PARISH, null));
        assertEquals("Giáo xứ Phú Cường", n.parishName());
        assertEquals("", n.subUnitName());
        assertEquals("Giáo xứ Phú Cường", n.fullName());
    }

    @Test
    void giaoHoUsesParentParishAsParishName() {
        Parish parent = unit("Phú Cường", ParishUnitType.PARISH, null);
        ParishUnitNames n = ParishUnitNames.of(unit("Bà Lụa", ParishUnitType.GIAO_HO, parent));
        assertEquals("Phú Cường", n.parishName());
        assertEquals("Giáo họ Bà Lụa", n.subUnitName());
        assertEquals("Giáo họ Bà Lụa – Giáo xứ Phú Cường", n.fullName());
    }

    @Test
    void doesNotRepeatLabelWhenNameAlreadyHasPrefix() {
        Parish parent = unit("Giáo xứ Chánh Tòa", ParishUnitType.PARISH, null);
        ParishUnitNames n = ParishUnitNames.of(unit("Giáo điểm Suối Giữa", ParishUnitType.GIAO_DIEM, parent));
        assertEquals("Giáo xứ Chánh Tòa", n.parishName());
        assertEquals("Giáo điểm Suối Giữa", n.subUnitName());
        assertEquals("Giáo điểm Suối Giữa – Giáo xứ Chánh Tòa", n.fullName());
    }

    @Test
    void subUnitWithoutParentFallsBackToOwnName() {
        ParishUnitNames n = ParishUnitNames.of(unit("Bà Lụa", ParishUnitType.GIAO_HO, null));
        assertEquals("Bà Lụa", n.parishName());
        assertEquals("Giáo họ Bà Lụa", n.subUnitName());
    }

    @Test
    void nullParishIsEmpty() {
        assertEquals(new ParishUnitNames("", "", ""), ParishUnitNames.of(null));
    }
}
