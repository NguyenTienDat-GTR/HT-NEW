package phucuong.ht.modules.parish.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParishPlaceTest {

    @Test
    void describeAddsLabelsWhenNamesHaveNone() {
        assertEquals("Giáo họ B (Giáo hạt X, Giáo phận P)", new ParishPlace("Giáo họ", "B", "X", "P").describe());
    }

    @Test
    void describeDoesNotRepeatLabelsAlreadyInNames() {
        assertEquals("Giáo xứ Phú Hòa (Giáo hạt Thủ Dầu Một, Giáo phận Phú Cường)",
                new ParishPlace("Giáo xứ", "Giáo xứ Phú Hòa", "Giáo hạt Thủ Dầu Một", "Phú Cường").describe());
    }
}
