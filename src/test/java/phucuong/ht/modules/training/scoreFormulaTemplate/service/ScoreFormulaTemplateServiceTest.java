package phucuong.ht.modules.training.scoreFormulaTemplate.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.request.ScoreFormulaTemplateUpdateRequest;
import phucuong.ht.modules.training.scoreFormulaTemplate.repository.ScoreFormulaTemplateRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreFormulaTemplateServiceTest {

    @Mock private ScoreFormulaTemplateRepository repository;
    @Mock private CommonValidationHelper commonValidationHelper;
    @InjectMocks private ScoreFormulaTemplateService service;

    @Test
    void updateShouldRejectFormulaWithScoredParticipations() {
        when(repository.hasScoredParticipations("formula-used")).thenReturn(true);
        when(commonValidationHelper.conflict("Không thể sửa công thức tính điểm đã có dữ liệu chấm điểm"))
                .thenReturn(org.mockito.Mockito.mock(ApiException.class));

        assertThrows(RuntimeException.class, () -> service.update(
                "formula-used", new ScoreFormulaTemplateUpdateRequest(), null));
    }
}
