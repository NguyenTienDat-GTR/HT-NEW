package phucuong.ht.common.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.MonthDay;

/** Lưu {@link MonthDay} vào cột VARCHAR(5) theo dạng "MM-dd" (ví dụ "06-16"). */
@Converter
public class MonthDayConverter implements AttributeConverter<MonthDay, String> {

    @Override
    public String convertToDatabaseColumn(MonthDay attribute) {
        return attribute == null ? null : String.format("%02d-%02d", attribute.getMonthValue(), attribute.getDayOfMonth());
    }

    @Override
    public MonthDay convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : MonthDay.parse("--" + dbData.trim());
    }
}
