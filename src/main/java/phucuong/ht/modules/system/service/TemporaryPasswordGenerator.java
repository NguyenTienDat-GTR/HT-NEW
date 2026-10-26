package phucuong.ht.modules.system.service;

import java.security.SecureRandom;

/**
 * Sinh mật khẩu tạm thời khi admin đặt lại mật khẩu: đúng 8 ký tự, chỉ gồm chữ thường và chữ hoa
 * (không số, không ký tự đặc biệt), có ít nhất một chữ hoa và một chữ thường.
 * Bỏ các chữ dễ nhầm với số hoặc nhau: I, l, O, o.
 */
public final class TemporaryPasswordGenerator {

    public static final int LENGTH = 8;

    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String ALL = UPPER + LOWER;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswordGenerator() {
    }

    public static String generate() {
        return generate(RANDOM);
    }

    static String generate(SecureRandom random) {
        char[] chars = new char[LENGTH];
        chars[0] = UPPER.charAt(random.nextInt(UPPER.length()));
        chars[1] = LOWER.charAt(random.nextInt(LOWER.length()));
        for (int index = 2; index < LENGTH; index++) {
            chars[index] = ALL.charAt(random.nextInt(ALL.length()));
        }
        // Xáo trộn để hai ký tự bắt buộc không luôn nằm ở đầu.
        for (int index = LENGTH - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            char temp = chars[index];
            chars[index] = chars[swap];
            chars[swap] = temp;
        }
        return new String(chars);
    }
}
