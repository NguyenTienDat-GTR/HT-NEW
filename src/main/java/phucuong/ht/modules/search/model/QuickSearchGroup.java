package phucuong.ht.modules.search.model;

import java.util.List;

/** type: leader | diocese | deanery | parish | course | certificate. */
public record QuickSearchGroup(String type, String label, List<QuickSearchItem> items) {
}
