package phucuong.ht.common.excel;

import phucuong.ht.modules.certificate.model.response.CertificateResponse;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.ComponentScoreResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Cột Excel của danh sách huynh trưởng, khóa huấn luyện, hồ sơ tham dự và chứng nhận. */
public final class TrainingExcelExports {

    private TrainingExcelExports() {
    }

    public static byte[] leaders(List<LeaderResponse> rows, Map<String, String> levelNames) {
        return ExcelExportWriter.write("Hồ sơ huynh trưởng", List.of(
                ExcelColumn.<LeaderResponse>of("Tên thánh", 16, LeaderResponse::getHolyName),
                ExcelColumn.<LeaderResponse>of("Họ và tên", 28, LeaderResponse::getFullName),
                ExcelColumn.<LeaderResponse>of("Ngày sinh", 14, LeaderResponse::getBirthDate),
                ExcelColumn.<LeaderResponse>of("Giới tính", 10, row -> ExcelLabels.gender(row.getGender())),
                ExcelColumn.<LeaderResponse>of("Email", 30, LeaderResponse::getEmail),
                ExcelColumn.<LeaderResponse>of("Số điện thoại", 16, LeaderResponse::getPhoneNumber),
                ExcelColumn.<LeaderResponse>of("Cấp độ", 24, row -> row.getLeaderLevel() == null ? "" : levelNames.getOrDefault(row.getLeaderLevel(), row.getLeaderLevel())),
                ExcelColumn.<LeaderResponse>of("Giáo hạt", 28, LeaderResponse::getDeaneryName),
                ExcelColumn.<LeaderResponse>of("Giáo xứ", 28, LeaderResponse::getParishName),
                ExcelColumn.<LeaderResponse>of("Trạng thái", 16, row -> ExcelLabels.status(row.getStatus())),
                ExcelColumn.<LeaderResponse>of("Ngày tạo", 18, LeaderResponse::getCreatedAt),
                ExcelColumn.<LeaderResponse>of("Người tạo", 22, LeaderResponse::getCreatedByName)
        ), rows);
    }

    public static byte[] courses(List<TrainingCourseResponse> rows) {
        return ExcelExportWriter.write("Khóa huấn luyện", List.of(
                ExcelColumn.<TrainingCourseResponse>of("Mã khóa", 38, TrainingCourseResponse::getCourseCode),
                ExcelColumn.<TrainingCourseResponse>of("Tên khóa", 36, TrainingCourseResponse::getCourseName),
                ExcelColumn.<TrainingCourseResponse>of("Loại khóa", 28, row -> Boolean.TRUE.equals(row.getPastCourse()) ? "Khóa quá khứ" : row.getCourseTypeName()),
                ExcelColumn.<TrainingCourseResponse>of("Cấp độ đạt được (khóa quá khứ)", 26, TrainingCourseResponse::getResultingLevel),
                ExcelColumn.<TrainingCourseResponse>of("Năm tổ chức", 12, TrainingCourseResponse::getHostYear),
                ExcelColumn.<TrainingCourseResponse>of("Đơn vị tổ chức", 28, row -> row.getDeaneryName() != null ? row.getDeaneryName() : row.getDioceseName()),
                ExcelColumn.<TrainingCourseResponse>of("Giáo phận", 24, TrainingCourseResponse::getDioceseName),
                ExcelColumn.<TrainingCourseResponse>of("Địa điểm", 28, TrainingCourseResponse::getLocation),
                ExcelColumn.<TrainingCourseResponse>of("Ngày bắt đầu", 14, TrainingCourseResponse::getStartDate),
                ExcelColumn.<TrainingCourseResponse>of("Ngày kết thúc", 14, TrainingCourseResponse::getEndDate),
                ExcelColumn.<TrainingCourseResponse>of("Bắt đầu đăng ký", 18, TrainingCourseResponse::getRegistrationStartAt),
                ExcelColumn.<TrainingCourseResponse>of("Kết thúc đăng ký", 18, TrainingCourseResponse::getRegistrationEndAt),
                ExcelColumn.<TrainingCourseResponse>of("Điểm đạt", 10, TrainingCourseResponse::getPassingScore),
                ExcelColumn.<TrainingCourseResponse>of("Số tham dự viên", 14, TrainingCourseResponse::getApprovedParticipantCount),
                ExcelColumn.<TrainingCourseResponse>of("Trạng thái đăng ký", 20, row -> ExcelLabels.registrationStatus(row.getRegistrationStatus())),
                ExcelColumn.<TrainingCourseResponse>of("Trạng thái tổ chức", 20, row -> ExcelLabels.organizingStatus(row.getOrganizingStatus())),
                ExcelColumn.<TrainingCourseResponse>of("Ghi chú", 30, TrainingCourseResponse::getAdditionalNotes),
                ExcelColumn.<TrainingCourseResponse>of("Trạng thái", 16, row -> ExcelLabels.status(row.getStatus())),
                ExcelColumn.<TrainingCourseResponse>of("Ngày tạo", 18, TrainingCourseResponse::getCreatedAt),
                ExcelColumn.<TrainingCourseResponse>of("Người tạo", 22, TrainingCourseResponse::getCreatedByName)
        ), rows);
    }

    public static byte[] participations(List<CourseParticipationResponse> rows) {
        return ExcelExportWriter.write("Hồ sơ tham dự", List.of(
                ExcelColumn.<CourseParticipationResponse>of("Tên thánh", 16, CourseParticipationResponse::getLeaderHolyName),
                ExcelColumn.<CourseParticipationResponse>of("Họ và tên", 28, CourseParticipationResponse::getLeaderFullName),
                ExcelColumn.<CourseParticipationResponse>of("Giáo xứ", 26, CourseParticipationResponse::getParishName),
                ExcelColumn.<CourseParticipationResponse>of("Giáo hạt", 26, CourseParticipationResponse::getDeaneryName),
                ExcelColumn.<CourseParticipationResponse>of("Khóa huấn luyện", 36, CourseParticipationResponse::getCourseName),
                ExcelColumn.<CourseParticipationResponse>of("Ngày đăng ký", 14, CourseParticipationResponse::getRegistrationDate),
                ExcelColumn.<CourseParticipationResponse>of("Trạng thái tham dự", 20, row -> ExcelLabels.participationStatus(row.getParticipationStatus())),
                ExcelColumn.<CourseParticipationResponse>of("Trạng thái duyệt đăng ký", 22, row -> ExcelLabels.approval(row.getApprovalStatus())),
                ExcelColumn.<CourseParticipationResponse>of("Cấp độ hiện tại", 22, CourseParticipationResponse::getLeaderLevel),
                ExcelColumn.<CourseParticipationResponse>of("Điểm đạt", 10, CourseParticipationResponse::getPassingScore),
                ExcelColumn.<CourseParticipationResponse>of("Tổng điểm", 10, row -> Boolean.TRUE.equals(row.getScoreHidden()) ? null : row.getTotalScore()),
                ExcelColumn.<CourseParticipationResponse>of("Điểm thành phần", 40, row -> Boolean.TRUE.equals(row.getScoreHidden()) ? null : componentScores(row.getComponentScores())),
                ExcelColumn.<CourseParticipationResponse>of("Kết quả", 16, row -> ExcelLabels.courseResult(row.getResult())),
                ExcelColumn.<CourseParticipationResponse>of("Đạt / Không đạt", 16, row -> ExcelLabels.finalResult(row.getFinalResult())),
                ExcelColumn.<CourseParticipationResponse>of("Nhận xét", 30, CourseParticipationResponse::getReview),
                ExcelColumn.<CourseParticipationResponse>of("Ghi chú điểm", 30, CourseParticipationResponse::getScoreNote),
                ExcelColumn.<CourseParticipationResponse>of("Lý do ngoại lệ", 30, CourseParticipationResponse::getExceptionReason),
                ExcelColumn.<CourseParticipationResponse>of("Trạng thái chứng nhận", 22, row -> ExcelLabels.approval(row.getCertificateApprovalStatus())),
                ExcelColumn.<CourseParticipationResponse>of("Ngày tạo", 18, CourseParticipationResponse::getCreatedAt),
                ExcelColumn.<CourseParticipationResponse>of("Người tạo", 22, CourseParticipationResponse::getCreatedByName)
        ), rows);
    }

    public static byte[] certificates(List<CertificateResponse> rows) {
        return ExcelExportWriter.write("Chứng nhận", List.of(
                ExcelColumn.<CertificateResponse>of("Mã chứng nhận", 30, CertificateResponse::getCertificateCode),
                ExcelColumn.<CertificateResponse>of("Mã chứng nhận cũ", 24, CertificateResponse::getOldCertificateCode),
                ExcelColumn.<CertificateResponse>of("Tên chứng nhận", 30, CertificateResponse::getCertificateName),
                ExcelColumn.<CertificateResponse>of("Huynh trưởng", 28, CertificateResponse::getLeaderFullName),
                ExcelColumn.<CertificateResponse>of("Khóa huấn luyện", 36, CertificateResponse::getCourseName),
                ExcelColumn.<CertificateResponse>of("Tổng điểm", 10, row -> Boolean.TRUE.equals(row.getScoreHidden()) ? null : row.getTotalScore()),
                ExcelColumn.<CertificateResponse>of("Điểm đạt", 10, CertificateResponse::getPassingScore),
                ExcelColumn.<CertificateResponse>of("Kết quả", 14, row -> ExcelLabels.passed(row.getPassed())),
                ExcelColumn.<CertificateResponse>of("Trạng thái duyệt", 18, row -> ExcelLabels.approval(row.getApprovalStatus())),
                ExcelColumn.<CertificateResponse>of("Loại người ký", 16, row -> ExcelLabels.signerType(row.getSignerType())),
                ExcelColumn.<CertificateResponse>of("Người ký", 26, CertificateResponse::getSignerName),
                ExcelColumn.<CertificateResponse>of("Chức vụ người ký", 26, CertificateResponse::getSignerTitle),
                ExcelColumn.<CertificateResponse>of("Ngày cấp", 14, CertificateResponse::getIssueDate),
                ExcelColumn.<CertificateResponse>of("Ngày duyệt", 18, CertificateResponse::getApprovedAt),
                ExcelColumn.<CertificateResponse>of("Người duyệt", 22, CertificateResponse::getApprovedByName),
                ExcelColumn.<CertificateResponse>of("Duyệt ngoại lệ", 14, row -> Boolean.TRUE.equals(row.getApprovedByException()) ? "Có" : "Không"),
                ExcelColumn.<CertificateResponse>of("Lý do duyệt ngoại lệ", 30, CertificateResponse::getExceptionApprovalReason),
                ExcelColumn.<CertificateResponse>of("Lý do từ chối", 30, CertificateResponse::getRejectionReason),
                ExcelColumn.<CertificateResponse>of("Ngày thu hồi", 18, CertificateResponse::getRevokedAt),
                ExcelColumn.<CertificateResponse>of("Lý do thu hồi", 30, CertificateResponse::getRevocationReason),
                ExcelColumn.<CertificateResponse>of("Ngày tạo", 18, CertificateResponse::getCreatedAt)
        ), rows);
    }

    private static String componentScores(List<ComponentScoreResponse> scores) {
        if (scores == null || scores.isEmpty()) {
            return "";
        }
        return scores.stream()
                .filter(score -> score.getScore() != null)
                .map(score -> score.getComponentName() + ": " + score.getScore().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("; "));
    }
}
