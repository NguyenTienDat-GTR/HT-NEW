package phucuong.ht.modules.training.registrationTemplate;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateCompressionTest {

    @Test
    void roundTripReturnsTheOriginalBytesAndStoresLess() throws Exception {
        byte[] original;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Mẫu");
            for (int r = 0; r < 200; r++) {
                sheet.createRow(r).createCell(0).setCellValue("Danh sách tham dự {{tenKhoa}} " + r);
            }
            workbook.write(out);
            original = out.toByteArray();
        }
        Class<?> type = Class.forName("phucuong.ht.modules.training.registrationTemplate.service.TemplateCompression");
        Method compress = type.getDeclaredMethod("compress", byte[].class);
        Method decompress = type.getDeclaredMethod("decompress", byte[].class);
        compress.setAccessible(true);
        decompress.setAccessible(true);
        byte[] stored = (byte[]) compress.invoke(null, (Object) original);
        assertTrue(stored.length <= original.length, "bản nén không được lớn hơn bản gốc quá nhiều");
        assertArrayEquals(original, (byte[]) decompress.invoke(null, (Object) stored));
    }
}
