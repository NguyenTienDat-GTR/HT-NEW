package phucuong.ht.common.excel;

import phucuong.ht.modules.certificate.model.enums.CertificateSignerType;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

/** Nhãn tiếng Việt cho các giá trị enum khi xuất Excel. */
public final class ExcelLabels {

    private ExcelLabels() {
    }

    public static String status(Boolean status) {
        return status == null ? "" : (status ? "Hoạt động" : "Ngừng hoạt động");
    }

    public static String gender(LeaderGender gender) {
        return gender == null ? "" : (gender == LeaderGender.NAM ? "Nam" : "Nữ");
    }

    public static String registrationStatus(CourseRegistrationStatus value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case NOT_STARTED -> "Chưa mở đăng ký";
            case OPEN -> "Đang mở đăng ký";
            case CLOSED -> "Đã đóng đăng ký";
            case ENDED -> "Đã kết thúc đăng ký";
        };
    }

    public static String organizingStatus(CourseOrganizingStatus value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case UPCOMING -> "Sắp diễn ra";
            case ONGOING -> "Đang diễn ra";
            case ENDED -> "Đã kết thúc";
        };
    }

    public static String participationStatus(ParticipationStatus value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case REGISTERED -> "Đã đăng ký";
            case ATTENDED -> "Đang tham dự";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
            case REJECTED -> "Bị từ chối";
        };
    }

    public static String courseResult(CourseResult value) {
        if (value == null) {
            return "";
        }
        return value == CourseResult.COMPLETED ? "Hoàn thành" : "Chưa hoàn thành";
    }

    public static String finalResult(CourseFinalResult value) {
        if (value == null) {
            return "";
        }
        return value == CourseFinalResult.PASSED ? "Đạt" : "Không đạt";
    }

    public static String approval(WorkflowApprovalStatus value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case NOT_REQUIRED -> "Không áp dụng";
            case PENDING -> "Chờ gửi duyệt";
            case SUBMITTED -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Từ chối";
            case REVOKED -> "Đã thu hồi";
        };
    }

    public static String signerType(CertificateSignerType value) {
        if (value == null) {
            return "";
        }
        return value == CertificateSignerType.CHAPLAIN ? "Cha tuyên úy" : "Khác";
    }

    public static String passed(Boolean value) {
        return value == null ? "" : (value ? "Đạt" : "Không đạt");
    }
}
