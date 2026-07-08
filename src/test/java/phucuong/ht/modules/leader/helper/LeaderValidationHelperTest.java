package phucuong.ht.modules.leader.helper;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderValidationHelperTest {

    private final LeaderRepository leaderRepository = mock(LeaderRepository.class);
    private final ParishRepository parishRepository = mock(ParishRepository.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(mock(securityUtils.class));
    private final LeaderValidationHelper helper = new LeaderValidationHelper(
            leaderRepository,
            parishRepository,
            commonValidationHelper
    );

    @Test
    void requireExistingLeaderWithParishShouldReturnEntity() {
        Leader leader = new Leader();
        UUID leaderId = UUID.randomUUID();
        leader.setId(leaderId);
        when(leaderRepository.findByIdWithParish(leaderId)).thenReturn(Optional.of(leader));

        assertSame(leader, helper.requireExistingLeaderWithParish(leaderId));
    }

    @Test
    void requireExistingParishShouldThrowWhenMissing() {
        UUID parishId = UUID.randomUUID();
        when(parishRepository.findById(parishId)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> helper.requireExistingParish(parishId));

        assertEquals("Không tìm thấy giáo xứ: " + parishId, exception.getMessage());
    }

    @Test
    void validateCreateEmailUniquenessShouldRejectDuplicateEmail() {
        when(leaderRepository.existsByEmail("leader@example.com")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateCreateEmailUniqueness("leader@example.com"));

        assertEquals("Email leader đã tồn tại", exception.getMessage());
    }

    @Test
    void validateUpdateEmailUniquenessShouldRejectDuplicateChangedEmail() {
        UUID leaderId = UUID.randomUUID();
        when(leaderRepository.existsByEmailAndIdNot("leader@example.com", leaderId)).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> helper.validateUpdateEmailUniqueness(leaderId, "leader@example.com", "old@example.com")
        );

        assertEquals("Email leader đã tồn tại", exception.getMessage());
    }

    @Test
    void validateCreateInputShouldNormalizeTextFields() {
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setHolyName("  Phaolo  ");
        request.setFullName("  Nguyễn An  ");
        request.setEmail("  leader@example.com  ");
        request.setPhoneNumber("");

        helper.validateCreateInput(request);

        assertEquals("Phaolo", request.getHolyName());
        assertEquals("Nguyễn An", request.getFullName());
        assertEquals("leader@example.com", request.getEmail());
        assertNull(request.getPhoneNumber());
    }

    @Test
    void splitFullNameShouldUseLastTokenAsFirstName() {
        LeaderValidationHelper.LeaderNameParts result = helper.splitFullName("Nguyễn Văn An");

        assertEquals("An", result.firstName());
        assertEquals("Nguyễn Văn", result.lastName());
    }
}
