package phucuong.ht.modules.system.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PermissionTaxonomyResponse {
    private List<TaxonomyOption> modules;
    private List<TaxonomyOption> resources;
    private List<TaxonomyOption> actions;
    private List<TaxonomyOption> scopes;

    @Getter
    @Builder
    public static class TaxonomyOption {
        private String code;
        private String name;
        private String parentCode;
        private String description;
    }
}
