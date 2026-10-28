package phucuong.ht.modules.parish.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ParishParentChangeRequest {

    @NotNull
    private UUID parentParishId;
}
