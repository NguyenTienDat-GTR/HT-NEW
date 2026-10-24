package phucuong.ht.modules.training.courseInvitation.model;

import jakarta.validation.constraints.Size;

import java.util.List;

// Yêu cầu / phản hồi của việc gửi email thông tin tham dự khóa huấn luyện.
public final class InvitationModels {

    private InvitationModels() {
    }

    // participationIds: null hoặc rỗng = toàn bộ hồ sơ trong danh sách tham dự.
    // includeQr: kèm mã QR điểm danh (chỉ khi khóa bật điểm danh). resend: gửi lại cả hồ sơ đã được gửi trước đó.
    public record SendRequest(@Size(max = 5000, message = "Mỗi lần gửi tối đa 5000 hồ sơ") List<String> participationIds,
                              boolean includeQr, boolean resend) {
    }

    // Một hồ sơ không gửi được: không có email, email sai định dạng, không còn trong danh sách tham dự hoặc lỗi gửi thư.
    public record Issue(String participationId, String holyName, String fullName, String email, String reason) {
    }

    // total = số hồ sơ được xét; sent = đã gửi; skippedAlreadySent = bỏ qua vì đã gửi trước đó (không bật gửi lại);
    // failed = số hồ sơ trong issues. issues luôn đủ để người dùng biết hồ sơ nào cần xử lý.
    public record Result(int total, int sent, int skippedAlreadySent, int failed, boolean includeQr, List<Issue> issues) {
    }

    public record JobResponse(String jobId, String status, Result result, String error) {
    }
}
