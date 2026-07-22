package phucuong.ht.modules.system.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RbacAuthRefreshNotifier {

    public static final String DESTINATION = "/queue/auth-events";

    private final SimpMessagingTemplate simpMessagingTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public RbacAuthRefreshNotifier(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void notifyAccount(String username, String reason) {
        notifyUsers(username == null ? List.of() : List.of(username), reason);
    }

    public void notifyRoleChanged(String roleCode, String reason) {
        notifyUsers(findUsernamesByRoleCode(roleCode), reason);
    }

    public void notifyPermissionChanged(String permissionCode, String reason) {
        notifyUsers(findUsernamesByPermissionCode(permissionCode), reason);
    }

    public void notifyUsers(Collection<String> usernames, String reason) {
        Set<String> recipients = normalizeUsernames(usernames);
        if (recipients.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendNow(recipients, reason);
                }
            });
            return;
        }
        sendNow(recipients, reason);
    }

    private List<String> findUsernamesByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                select distinct ar.username
                from account_role ar
                where ar.role_code = :roleCode
                """)
                .setParameter("roleCode", roleCode)
                .getResultList();
        return rows.stream().map(String::valueOf).toList();
    }

    private List<String> findUsernamesByPermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                select distinct affected.username
                from (
                    select ar.username
                    from account_role ar
                    join role_permission rp on rp.role_code = ar.role_code
                    where rp.permission_code = :permissionCode
                    union
                    select apo.username
                    from account_permission_override apo
                    where apo.permission_code = :permissionCode
                ) affected
                """)
                .setParameter("permissionCode", permissionCode)
                .getResultList();
        return rows.stream().map(String::valueOf).toList();
    }

    private Set<String> normalizeUsernames(Collection<String> usernames) {
        Set<String> recipients = new LinkedHashSet<>();
        if (usernames == null) {
            return recipients;
        }
        for (String username : usernames) {
            if (username != null && !username.isBlank()) {
                recipients.add(username);
            }
        }
        return recipients;
    }

    private void sendNow(Set<String> recipients, String reason) {
        AuthRefreshEvent event = new AuthRefreshEvent("AUTH_REFRESH_REQUIRED", reason, OffsetDateTime.now());
        recipients.forEach(username -> simpMessagingTemplate.convertAndSendToUser(username, DESTINATION, event));
    }

    public record AuthRefreshEvent(String type, String reason, OffsetDateTime occurredAt) {
    }
}
