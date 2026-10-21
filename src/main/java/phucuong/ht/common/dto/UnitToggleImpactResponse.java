package phucuong.ht.common.dto;

// Số liệu tác động khi tắt một đơn vị: hiển thị cho người dùng trước khi xác nhận.
public record UnitToggleImpactResponse(
        String unitType,
        boolean currentlyActive,
        long childDeaneries,
        long childParishes,
        long affectedAccounts,
        long leaders,
        long openCourses,
        long pendingRegistrations,
        long pendingCertificates
) {
}
