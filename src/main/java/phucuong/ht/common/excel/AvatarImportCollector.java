package phucuong.ht.common.excel;

import java.util.List;
import java.util.UUID;

/** Gom kế hoạch/cảnh báo ảnh trong pha import dữ liệu. */
public final class AvatarImportCollector {

    private AvatarImportCollector() {
    }

    public static void collect(AvatarZip images, List<AvatarPlan> plans, List<AvatarWarning> warnings,
                               int row, UUID leaderId, String fullName, String fileName, UUID dioceseId) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        if (images == null) {
            warnings.add(new AvatarWarning(row, fullName, fileName, "Không có file ZIP ảnh"));
            return;
        }
        AvatarZip.Lookup lookup = images.lookup(fileName);
        if (!lookup.found()) {
            warnings.add(new AvatarWarning(row, fullName, fileName, lookup.reason()));
            return;
        }
        plans.add(new AvatarPlan(row, leaderId, fullName, fileName, dioceseId));
    }
}
