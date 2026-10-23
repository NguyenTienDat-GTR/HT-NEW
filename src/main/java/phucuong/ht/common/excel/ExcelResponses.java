package phucuong.ht.common.excel;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;

public final class ExcelResponses {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ExcelResponses() {
    }

    /** Trả file .xlsx; tên file bỏ dấu và có thêm ngày xuất. */
    public static ResponseEntity<byte[]> xlsx(byte[] content, String baseName) {
        String ascii = Normalizer.normalize(baseName, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').replaceAll("[^A-Za-z0-9_-]+", "-");
        String fileName = ascii + "-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(XLSX)
                .contentLength(content.length)
                .body(content);
    }
}
