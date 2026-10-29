package phucuong.ht.modules.media.model.enums;

import java.util.Set;

public final class MediaVariants {

    public static final String THUMB = "thumb";
    public static final String MEDIUM = "medium";
    public static final String MASTER = "master";
    public static final String PREVIEW = "preview";

    public static final Set<String> ALL = Set.of(THUMB, MEDIUM, MASTER, PREVIEW);

    private MediaVariants() {
    }
}
