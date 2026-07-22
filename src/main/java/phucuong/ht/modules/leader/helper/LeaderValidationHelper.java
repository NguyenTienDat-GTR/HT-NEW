package phucuong.ht.modules.leader.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeaderValidationHelper {

    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final CommonValidationHelper commonValidationHelper;

    public void validateLeaderId(UUID leaderId) {
        if (leaderId == null) {
            throw commonValidationHelper.validationError("ID leader là bắt buộc");
        }
    }

    public Leader requireExistingLeader(UUID leaderId) {
        validateLeaderId(leaderId);
        return leaderRepository.findById(leaderId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy Huynh Trưởng: " + leaderId));
    }

    public Leader requireExistingLeaderWithParish(UUID leaderId) {
        validateLeaderId(leaderId);
        return leaderRepository.findByIdWithParish(leaderId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy Huynh Trưởng: " + leaderId));
    }

    public Parish requireExistingParish(UUID parishId) {
        commonValidationHelper.requireId(parishId, "ID giáo xứ là bắt buộc");
        return parishRepository.findById(parishId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ: " + parishId));
    }

    public void validateCreateInput(LeaderCreateRequest request) {
        if (request == null) {
            return;
        }

        request.setHolyName(commonValidationHelper.normalizeRequiredText(request.getHolyName()));
        request.setFullName(commonValidationHelper.normalizeRequiredText(request.getFullName()));
        request.setEmail(commonValidationHelper.normalizeRequiredText(request.getEmail()));
        request.setPhoneNumber(commonValidationHelper.normalizeOptionalText(request.getPhoneNumber()));
    }

    public void validateUpdateInput(LeaderUpdateRequest request) {
        if (request == null) {
            return;
        }

        request.setHolyName(commonValidationHelper.normalizeOptionalText(request.getHolyName()));
        request.setEmail(commonValidationHelper.normalizeOptionalText(request.getEmail()));
        request.setPhoneNumber(commonValidationHelper.normalizeOptionalText(request.getPhoneNumber()));
    }

    public void validateCreateEmailUniqueness(String email) {
        if (leaderRepository.existsByEmail(email)) {
            throw commonValidationHelper.conflict("Email Huynh Trưởng đã tồn tại");
        }
    }

    public void validateUpdateEmailUniqueness(UUID leaderId, String requestedEmail, String currentEmail) {
        if (!commonValidationHelper.isChanged(requestedEmail, currentEmail)) {
            return;
        }

        if (leaderRepository.existsByEmailAndIdNot(requestedEmail, leaderId)) {
            throw commonValidationHelper.conflict("Email Huynh Trưởng đã tồn tại");
        }
    }

    public LeaderNameParts splitFullName(String fullName) {
        String normalized = commonValidationHelper.normalizeRequiredText(fullName);
        if (normalized == null || normalized.isBlank()) {
            throw commonValidationHelper.validationError("Họ và tên Huynh Trưởng là bắt buộc");
        }

        String[] parts = Arrays.stream(normalized.split("\\s+"))
                .filter(part -> !part.isBlank())
                .toArray(String[]::new);
        if (parts.length == 0) {
            throw commonValidationHelper.validationError("Họ và tên Huynh Trưởng là bắt buộc");
        }

        String firstName = parts[parts.length - 1];
        String lastName = parts.length == 1 ? null : String.join(" ", Arrays.copyOf(parts, parts.length - 1));
        return new LeaderNameParts(firstName, lastName);
    }

    public String getRequiredAuditActor(Authentication authentication) {
        return commonValidationHelper.requireAuditActor(
                authentication,
                "Tài khoản hiện tại không có thông tin người thực hiện để ghi nhận lịch sử"
        );
    }

    public void validateToggleConfirmation(String confirmationText) {
        commonValidationHelper.requireStatusToggleConfirmation(confirmationText);
    }

    public record LeaderNameParts(String firstName, String lastName) {
    }
}
