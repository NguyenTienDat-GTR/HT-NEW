package phucuong.ht.modules.media.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * Đặt tên thư mục giáo phận trên cloud: tên không dấu (slug) + 8 ký tự đầu của id.
 * Quy tắc phải khớp với phần backfill trong migration V110.
 */
public final class MediaFolderNamer {

    private MediaFolderNamer() {
    }

    public static String dioceseFolder(String name, UUID id) {
        String slug = slug(name);
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-+$", "");
        }
        String shortId = id.toString().replace("-", "").substring(0, 8);
        return slug.isEmpty() ? shortId : slug + "-" + shortId;
    }

    static String slug(String name) {
        if (name == null) {
            return "";
        }
        String base = name.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
