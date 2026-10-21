package phucuong.ht.modules.training.enums;

public enum WorkflowApprovalStatus {
    NOT_REQUIRED,
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED,
    // Chứng nhận đã duyệt nhưng bị thu hồi: hồ sơ liên kết bị coi là không đạt.
    REVOKED
}
