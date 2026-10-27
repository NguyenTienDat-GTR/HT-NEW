package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailUpdateResponse {

    private final String email;
}
