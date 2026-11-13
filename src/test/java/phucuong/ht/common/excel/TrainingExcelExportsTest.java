package phucuong.ht.common.excel;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.training.courseParticipation.model.response.ComponentScoreResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingExcelExportsTest {

    private static ComponentScoreResponse score(String id, String name, int order, String value) {
        return ComponentScoreResponse.builder().componentId(id).componentName(name).displayOrder(order)
                .score(value == null ? null : new BigDecimal(value)).build();
    }

    private static CourseParticipationResponse row(boolean hidden, ComponentScoreResponse... scores) {
        return CourseParticipationResponse.builder().scoreHidden(hidden).componentScores(List.of(scores)).build();
    }

    private static List<ExcelColumn<CourseParticipationResponse>> pick(String... headers) {
        List<String> wanted = List.of(headers);
        return TrainingExcelExports.participationColumns().stream().filter(column -> wanted.contains(column.header())).toList();
    }

    @Test
    void splitsComponentScoresIntoOneColumnPerComponentInDisplayOrder() {
        List<CourseParticipationResponse> rows = List.of(
                row(false, score("b", "Chuyên cần", 2, "8"), score("a", "Lý thuyết", 1, "7.5")),
                row(false, score("a", "Lý thuyết", 1, "9")),
                row(true, score("a", "Lý thuyết", 1, "10")));

        List<ExcelColumn<CourseParticipationResponse>> columns = TrainingExcelExports.expandComponentScores(
                pick("Họ và tên", TrainingExcelExports.COMPONENT_SCORES, "Tổng điểm"), rows);

        assertEquals(List.of("Họ và tên", "Tổng điểm", "Lý thuyết", "Chuyên cần"), ExcelColumns.headers(columns));
        assertEquals(new BigDecimal("7.5"), columns.get(2).value().apply(rows.get(0)));
        assertEquals(new BigDecimal("8"), columns.get(3).value().apply(rows.get(0)));
        assertEquals(new BigDecimal("9"), columns.get(2).value().apply(rows.get(1)));
        assertNull(columns.get(3).value().apply(rows.get(1)), "thành phần chưa có điểm thì để trống");
        assertNull(columns.get(2).value().apply(rows.get(2)), "điểm bị ẩn thì không xuất");
    }

    @Test
    void keepsColumnsUntouchedWhenComponentScoresNotSelectedOrAbsent() {
        List<CourseParticipationResponse> rows = List.of(row(false, score("a", "Lý thuyết", 1, "7")));
        List<ExcelColumn<CourseParticipationResponse>> withoutComponents = pick("Họ và tên", "Tổng điểm");
        assertEquals(withoutComponents, TrainingExcelExports.expandComponentScores(withoutComponents, rows));

        List<ExcelColumn<CourseParticipationResponse>> placeholder = pick("Họ và tên", TrainingExcelExports.COMPONENT_SCORES);
        List<ExcelColumn<CourseParticipationResponse>> noScores = TrainingExcelExports.expandComponentScores(placeholder, List.of(row(false)));
        assertEquals(ExcelColumns.headers(placeholder), ExcelColumns.headers(noScores));
    }

    @Test
    void keepsHeadersUniqueWhenComponentNameClashesWithAnotherColumn() {
        List<CourseParticipationResponse> rows = List.of(row(false, score("a", "Tổng điểm", 1, "5")));
        List<String> headers = ExcelColumns.headers(TrainingExcelExports.expandComponentScores(
                pick(TrainingExcelExports.COMPONENT_SCORES, "Tổng điểm"), rows));
        assertEquals(2, headers.size());
        assertTrue(headers.stream().distinct().count() == 2);
    }
}
