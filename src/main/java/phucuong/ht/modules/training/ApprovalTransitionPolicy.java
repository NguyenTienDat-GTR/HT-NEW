package phucuong.ht.modules.training;

import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

/**
 * Quy tắc chuyển trạng thái duyệt ({@link WorkflowApprovalStatus}) dùng chung cho hồ sơ tham dự và chứng nhận,
 * thay cho các phép so sánh trạng thái viết tay rải rác ở từng service.
 */
public final class ApprovalTransitionPolicy {

    private ApprovalTransitionPolicy() {
    }

    /** Đang chờ người có thẩm quyền quyết định (chưa nộp lên cấp trên hoặc đã nộp). */
    public static boolean isAwaitingDecision(WorkflowApprovalStatus status) {
        return status == WorkflowApprovalStatus.PENDING || status == WorkflowApprovalStatus.SUBMITTED;
    }

    /** Hồ sơ chờ duyệt được duyệt hoặc từ chối; hồ sơ đã bị từ chối chỉ được duyệt lại. */
    public static boolean canDecideManually(WorkflowApprovalStatus status, boolean approved) {
        return isAwaitingDecision(status) || (approved && status == WorkflowApprovalStatus.REJECTED);
    }

    /** Chứng nhận đã duyệt hoặc đã thu hồi thì không duyệt lại. */
    public static boolean canApprove(WorkflowApprovalStatus status) {
        return status != WorkflowApprovalStatus.APPROVED && status != WorkflowApprovalStatus.REVOKED;
    }

    /** Chỉ từ chối được khi đang chờ duyệt. */
    public static boolean canReject(WorkflowApprovalStatus status) {
        return isAwaitingDecision(status);
    }
}
