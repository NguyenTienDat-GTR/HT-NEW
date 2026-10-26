package phucuong.ht.modules.auth.service;

/** Che địa chỉ email khi hiển thị cho người dùng, ví dụ {@code nguyenvan@gmail.com} thành {@code ng***@g***.com}. */
public final class EmailMasker {

    private static final String MASK = "***";

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return MASK;
        }

        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String maskedLocal = (local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2)) + MASK;

        int lastDot = domain.lastIndexOf('.');
        String host = lastDot > 0 ? domain.substring(0, lastDot) : domain;
        String suffix = lastDot > 0 ? domain.substring(lastDot) : "";
        String maskedHost = host.isEmpty() ? MASK : host.charAt(0) + MASK;

        return maskedLocal + "@" + maskedHost + suffix;
    }
}
