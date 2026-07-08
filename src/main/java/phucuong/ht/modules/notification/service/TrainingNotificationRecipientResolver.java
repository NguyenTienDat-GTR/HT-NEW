package phucuong.ht.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AuthPermissionQueryRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingNotificationRecipientResolver {

    private final AuthPermissionQueryRepository authPermissionQueryRepository;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper commonValidationHelper;

    public List<String> resolveRecipients(List<String> requestedUsernames, String permissionCode, UnitType scopeType, UUID scopeId) {
        List<String> candidates = (requestedUsernames == null || requestedUsernames.isEmpty())
                ? authPermissionQueryRepository.findUsernamesByPermissionCode(permissionCode)
                : requestedUsernames;

        List<String> resolved = candidates.stream()
                .filter(username -> username != null && !username.isBlank())
                .map(String::trim)
                .distinct()
                .filter(username -> isEligibleRecipient(username, permissionCode, scopeType, scopeId))
                .toList();

        if (requestedUsernames != null && !requestedUsernames.isEmpty()) {
            LinkedHashSet<String> unresolved = new LinkedHashSet<>(requestedUsernames.stream()
                    .filter(username -> username != null && !username.isBlank())
                    .map(String::trim)
                    .toList());
            unresolved.removeAll(resolved);
            if (!unresolved.isEmpty()) {
                throw commonValidationHelper.validationError("Nguoi nhan thong bao khong hop le: " + unresolved.iterator().next());
            }
        }

        return resolved;
    }

    private boolean isEligibleRecipient(String username, String permissionCode, UnitType scopeType, UUID scopeId) {
        if (!authPermissionQueryRepository.existsEffectivePermissionByUsername(username, permissionCode)) {
            return false;
        }

        return switch (scopeType) {
            case DEANERY -> accountRepository.findActiveDeaneryIdByUsername(username)
                    .map(scopeId::equals)
                    .orElse(false);
            case DIOCESE -> accountRepository.findActiveDioceseIdByUsername(username)
                    .map(scopeId::equals)
                    .orElse(false);
            case PARISH -> accountRepository.findActiveParishIdByUsername(username)
                    .map(scopeId::equals)
                    .orElse(false);
        };
    }
}
