package phucuong.ht.common.excel;

import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.diocese.model.response.DioceseResponse;
import phucuong.ht.modules.parish.model.response.ParishResponse;

import java.util.List;

/** Cột Excel của danh sách giáo phận, giáo hạt, giáo xứ. */
public final class OrganizationExcelExports {

    private OrganizationExcelExports() {
    }

    public static byte[] dioceses(List<DioceseResponse> rows) {
        return ExcelExportWriter.write("Giáo phận", List.of(
                ExcelColumn.<DioceseResponse>of("Tên giáo phận", 32, DioceseResponse::getName),
                ExcelColumn.<DioceseResponse>of("Đức cha / Cha tuyên úy", 28, DioceseResponse::getChaplain),
                ExcelColumn.<DioceseResponse>of("Tên liên đoàn", 28, DioceseResponse::getUnionName),
                ExcelColumn.<DioceseResponse>of("Ngày bổn mạng", 16, DioceseResponse::getPatronSaintDay),
                ExcelColumn.<DioceseResponse>of("Ngày thành lập", 16, DioceseResponse::getEstablishmentDate),
                ExcelColumn.<DioceseResponse>of("Số điện thoại", 16, DioceseResponse::getPhoneNumber),
                ExcelColumn.<DioceseResponse>of("Email", 28, DioceseResponse::getEmail),
                ExcelColumn.<DioceseResponse>of("Số giáo hạt", 12, DioceseResponse::getDeaneryCount),
                ExcelColumn.<DioceseResponse>of("Số giáo xứ", 12, DioceseResponse::getParishCount),
                ExcelColumn.<DioceseResponse>of("Số huynh trưởng", 14, DioceseResponse::getLeaderCount),
                ExcelColumn.<DioceseResponse>of("Trạng thái", 16, row -> ExcelLabels.status(row.getStatus())),
                ExcelColumn.<DioceseResponse>of("Ngày tạo", 18, DioceseResponse::getCreatedAt),
                ExcelColumn.<DioceseResponse>of("Người tạo", 22, DioceseResponse::getCreatedByName)
        ), rows);
    }

    public static byte[] deaneries(List<DeaneryResponse> rows) {
        return ExcelExportWriter.write("Giáo hạt", List.of(
                ExcelColumn.<DeaneryResponse>of("Tên giáo hạt", 32, DeaneryResponse::getName),
                ExcelColumn.<DeaneryResponse>of("Giáo phận", 28, DeaneryResponse::getDioceseName),
                ExcelColumn.<DeaneryResponse>of("Cha tuyên úy", 28, DeaneryResponse::getChaplain),
                ExcelColumn.<DeaneryResponse>of("Tên phân đoàn / hội", 28, DeaneryResponse::getAssociationName),
                ExcelColumn.<DeaneryResponse>of("Ngày bổn mạng", 16, DeaneryResponse::getPatronSaintDay),
                ExcelColumn.<DeaneryResponse>of("Ngày thành lập", 16, DeaneryResponse::getEstablishmentDate),
                ExcelColumn.<DeaneryResponse>of("Số điện thoại", 16, DeaneryResponse::getPhoneNumber),
                ExcelColumn.<DeaneryResponse>of("Email", 28, DeaneryResponse::getEmail),
                ExcelColumn.<DeaneryResponse>of("Số giáo xứ", 12, DeaneryResponse::getParishCount),
                ExcelColumn.<DeaneryResponse>of("Số huynh trưởng", 14, DeaneryResponse::getLeaderCount),
                ExcelColumn.<DeaneryResponse>of("Trạng thái", 16, row -> ExcelLabels.status(row.getStatus())),
                ExcelColumn.<DeaneryResponse>of("Trạng thái hiệu lực", 18, row -> ExcelLabels.status(row.getEffectiveStatus())),
                ExcelColumn.<DeaneryResponse>of("Ngày tạo", 18, DeaneryResponse::getCreatedAt),
                ExcelColumn.<DeaneryResponse>of("Người tạo", 22, DeaneryResponse::getCreatedByName)
        ), rows);
    }

    public static byte[] parishes(List<ParishResponse> rows) {
        return ExcelExportWriter.write("Giáo xứ", List.of(
                ExcelColumn.<ParishResponse>of("Tên giáo xứ", 32, ParishResponse::getName),
                ExcelColumn.<ParishResponse>of("Giáo hạt", 28, ParishResponse::getDeaneryName),
                ExcelColumn.<ParishResponse>of("Giáo phận", 28, ParishResponse::getDioceseName),
                ExcelColumn.<ParishResponse>of("Cha chánh xứ", 28, ParishResponse::getChaplain),
                ExcelColumn.<ParishResponse>of("Tên chi đoàn / chi hội", 28, ParishResponse::getChapterName),
                ExcelColumn.<ParishResponse>of("Ngày bổn mạng", 16, ParishResponse::getPatronSaintDay),
                ExcelColumn.<ParishResponse>of("Ngày thành lập", 16, ParishResponse::getEstablishmentDate),
                ExcelColumn.<ParishResponse>of("Số điện thoại", 16, ParishResponse::getPhoneNumber),
                ExcelColumn.<ParishResponse>of("Email", 28, ParishResponse::getEmail),
                ExcelColumn.<ParishResponse>of("Số huynh trưởng", 14, ParishResponse::getLeaderCount),
                ExcelColumn.<ParishResponse>of("Trạng thái", 16, row -> ExcelLabels.status(row.getStatus())),
                ExcelColumn.<ParishResponse>of("Trạng thái hiệu lực", 18, row -> ExcelLabels.status(row.getEffectiveStatus())),
                ExcelColumn.<ParishResponse>of("Ngày tạo", 18, ParishResponse::getCreatedAt),
                ExcelColumn.<ParishResponse>of("Người tạo", 22, ParishResponse::getCreatedByName)
        ), rows);
    }
}
