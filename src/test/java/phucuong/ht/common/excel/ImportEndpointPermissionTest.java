package phucuong.ht.common.excel;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import phucuong.ht.modules.deanery.controller.DeaneryController;
import phucuong.ht.modules.parish.controller.ParishController;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Import Excel và tải file mẫu giáo hạt / giáo xứ phải đi đúng quyền tạo giáo hạt / giáo xứ. */
class ImportEndpointPermissionTest {

    private static String preAuthorize(Class<?> controller, String method) {
        Method found = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có phương thức " + method));
        PreAuthorize annotation = found.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, method + " thiếu @PreAuthorize");
        return annotation.value();
    }

    @Test
    void deaneryImportAndTemplateUseTheCreatePermission() {
        String create = preAuthorize(DeaneryController.class, "create");
        assertEquals("@deaneryAccessEvaluator.canCreate(authentication)", create);
        assertEquals(create, preAuthorize(DeaneryController.class, "importDeaneries"));
        assertEquals(create, preAuthorize(DeaneryController.class, "importTemplate"));
    }

    @Test
    void parishImportAndTemplateUseTheCreatePermission() {
        String create = preAuthorize(ParishController.class, "create");
        assertEquals("@parishAccessEvaluator.canCreate(authentication)", create);
        assertEquals(create, preAuthorize(ParishController.class, "importParishes"));
        assertEquals(create, preAuthorize(ParishController.class, "importTemplate"));
    }
}
