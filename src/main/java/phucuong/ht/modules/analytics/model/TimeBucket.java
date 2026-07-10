package phucuong.ht.modules.analytics.model;

import java.util.Locale;

public enum TimeBucket {
    MONTH, QUARTER, YEAR;

    public static TimeBucket from(String value) {
        if (value == null || value.isBlank()) return MONTH;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("timeBucket chỉ nhận month, quarter hoặc year"); }
    }
}
