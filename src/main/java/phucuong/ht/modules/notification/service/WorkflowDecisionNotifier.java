package phucuong.ht.modules.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.AuditActorNameResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Gửi thông báo kết quả duyệt / từ chối / thu hồi / cấp lại hồ sơ đăng ký và chứng nhận tới tài khoản người gửi đăng ký
// và tài khoản admin giáo xứ của hồ sơ. Từ GROUP_THRESHOLD hồ sơ cùng khóa trong một lần xử lý thì gom thành một thông báo,
// danh sách hồ sơ nằm trong payload để FE hiển thị ở modal chi tiết.
@Service
@RequiredArgsConstructor
public class WorkflowDecisionNotifier {

    public static final int GROUP_THRESHOLD = 3;
    private static final String PARISH_ADMIN_ROLE = "ADMIN_PARISH";

    private final TrainingNotificationService trainingNotificationService;
    private final AuditActorNameResolver auditActorNameResolver;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public enum Decision {
        REGISTRATION_AUTO_APPROVED("training.registration.auto_approved_notice", "Hồ sơ đăng ký được tự động duyệt", "đăng ký", "được hệ thống tự động duyệt"),
        REGISTRATION_APPROVED("training.registration.approved", "Hồ sơ đăng ký đã được duyệt", "đăng ký", "được duyệt"),
        REGISTRATION_REJECTED("training.registration.rejected", "Hồ sơ đăng ký không được duyệt", "đăng ký", "không được duyệt"),
        REGISTRATION_REMOVED("training.participant.removed_notice", "Hồ sơ bị xóa khỏi danh sách tham dự", "đăng ký", "bị xóa khỏi danh sách tham dự"),
        CERTIFICATE_APPROVED("training.certificate.approved", "Chứng nhận đã được duyệt", "chứng nhận", "được duyệt"),
        CERTIFICATE_REJECTED("training.certificate.rejected", "Chứng nhận không được duyệt", "chứng nhận", "không được duyệt"),
        CERTIFICATE_REVOKED("training.certificate.revoked", "Chứng nhận đã bị thu hồi", "chứng nhận", "bị thu hồi"),
        CERTIFICATE_REISSUED("training.certificate.reissued", "Chứng nhận đã được cấp lại", "chứng nhận", "được cấp lại");

        private final String eventType;
        private final String title;
        private final String noun;
        private final String verb;

        Decision(String eventType, String title, String noun, String verb) {
            this.eventType = eventType;
            this.title = title;
            this.noun = noun;
            this.verb = verb;
        }

        public String eventType() {
            return eventType;
        }
    }

    /**
     * Một hồ sơ trong lần xử lý.
     *
     * @param submitterActor giá trị createdBy của hồ sơ đăng ký (mã huynh trưởng hoặc username của người gửi)
     * @param note           lý do từ chối / thu hồi / cấp lại, có thể rỗng
     * @param leaderId       huynh trưởng của hồ sơ; có tài khoản đang hoạt động thì cũng nhận thông báo về hồ sơ / chứng nhận của chính mình
     */
    public record Entry(String submitterActor, UUID parishId, String parishName, String courseCode, String courseName,
                        String leaderName, String documentCode, String note, UUID leaderId) {

        public Entry(String submitterActor, UUID parishId, String parishName, String courseCode, String courseName,
                     String leaderName, String documentCode, String note) {
            this(submitterActor, parishId, parishName, courseCode, courseName, leaderName, documentCode, note, null);
        }
    }

    public void notifyDecisions(Decision decision, List<Entry> entries, String actor) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Map<UUID, Set<String>> parishAdmins = new LinkedHashMap<>();
        Map<String, Set<String>> submitters = new LinkedHashMap<>();
        Map<String, Map<String, List<Entry>>> byRecipient = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (String recipient : recipientsOf(entry, parishAdmins, submitters)) {
                byRecipient.computeIfAbsent(recipient, key -> new LinkedHashMap<>())
                        .computeIfAbsent(String.valueOf(entry.courseCode()), key -> new ArrayList<>())
                        .add(entry);
            }
        }

        String actorName = actorName(actor);
        Map<Entry, List<String>> singles = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<Entry>>> recipientGroup : byRecipient.entrySet()) {
            for (List<Entry> courseEntries : recipientGroup.getValue().values()) {
                if (courseEntries.size() >= GROUP_THRESHOLD) {
                    trainingNotificationService.notifyUsers(List.of(recipientGroup.getKey()), groupCommand(decision, courseEntries, actor, actorName));
                    continue;
                }
                courseEntries.forEach(entry -> singles.computeIfAbsent(entry, key -> new ArrayList<>()).add(recipientGroup.getKey()));
            }
        }
        singles.forEach((entry, recipients) ->
                trainingNotificationService.notifyUsers(recipients, singleCommand(decision, entry, actor, actorName)));
    }

    private TrainingNotificationCommand singleCommand(Decision decision, Entry entry, String actor, String actorName) {
        StringBuilder message = new StringBuilder("Hồ sơ ").append(decision.noun).append(" của huynh trưởng ").append(entry.leaderName());
        if (hasText(entry.parishName())) {
            message.append(" (").append(entry.parishName()).append(")");
        }
        message.append(" khóa ").append(courseLabel(entry)).append(" ").append(decision.verb);
        if (hasText(actorName)) {
            message.append(" bởi ").append(actorName);
        }
        message.append(".");
        if (hasText(entry.note())) {
            message.append(" Lý do: ").append(entry.note().trim()).append(".");
        }
        message.append(" Chọn thông báo để xem chi tiết.");
        return TrainingNotificationCommand.builder()
                .eventType(decision.eventType)
                .title(decision.title)
                .message(message.toString())
                .senderUnitType(entry.parishId() == null ? null : UnitType.PARISH)
                .senderUnitId(entry.parishId())
                .courseCode(entry.courseCode())
                .totalCount(1)
                // Thông báo lẻ cũng mở bảng chi tiết (khóa, huynh trưởng, giáo xứ / mã chứng nhận, lý do, người thực hiện) như thông báo phân quyền.
                .payload(toJson(payloadOf(decision, List.of(entry), actorName)))
                .actor(actor)
                .build();
    }

    private TrainingNotificationCommand groupCommand(Decision decision, List<Entry> entries, String actor, String actorName) {
        Entry first = entries.get(0);
        String title = entries.size() + " hồ sơ " + decision.noun + " " + decision.verb;
        StringBuilder message = new StringBuilder(String.valueOf(entries.size())).append(" hồ sơ ").append(decision.noun)
                .append(" khóa ").append(courseLabel(first)).append(" ").append(decision.verb);
        if (hasText(actorName)) {
            message.append(" bởi ").append(actorName);
        }
        message.append(". Chọn thông báo để xem danh sách hồ sơ.");

        Map<String, Object> payload = payloadOf(decision, entries, actorName);
        return TrainingNotificationCommand.builder()
                .eventType(decision.eventType)
                .title(title)
                .message(message.toString())
                .courseCode(first.courseCode())
                .totalCount(entries.size())
                .payload(toJson(payload))
                .actor(actor)
                .build();
    }

    // Nội dung bảng chi tiết dùng chung cho thông báo lẻ và thông báo gộp.
    private Map<String, Object> payloadOf(Decision decision, List<Entry> entries, String actorName) {
        Entry first = entries.get(0);
        List<Map<String, String>> items = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("label", entry.leaderName());
            item.put("detail", joinDetail(entry));
            if (hasText(entry.note())) {
                item.put("note", entry.note().trim());
            }
            items.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "DECISION_LIST");
        payload.put("decision", decision.name());
        payload.put("heading", "Khóa " + courseLabel(first));
        if (hasText(actorName)) {
            payload.put("actorName", actorName);
        }
        payload.put("items", items);
        return payload;
    }

    private String joinDetail(Entry entry) {
        List<String> parts = new ArrayList<>();
        if (hasText(entry.parishName())) {
            parts.add(entry.parishName());
        }
        if (hasText(entry.documentCode())) {
            parts.add("Chứng nhận " + entry.documentCode());
        }
        return String.join(" · ", parts);
    }

    private String courseLabel(Entry entry) {
        return hasText(entry.courseName()) ? entry.courseName() : String.valueOf(entry.courseCode());
    }

    private String actorName(String actor) {
        if (!hasText(actor)) {
            return null;
        }
        return auditActorNameResolver.resolve(actor);
    }

    private Set<String> recipientsOf(Entry entry, Map<UUID, Set<String>> parishAdminCache, Map<String, Set<String>> submitterCache) {
        Set<String> recipients = new LinkedHashSet<>();
        if (hasText(entry.submitterActor())) {
            recipients.addAll(submitterCache.computeIfAbsent(entry.submitterActor(), this::findSubmitterAccounts));
        }
        if (entry.parishId() != null) {
            recipients.addAll(parishAdminCache.computeIfAbsent(entry.parishId(), this::findParishAdmins));
        }
        // Chính huynh trưởng (nếu có tài khoản) cũng nhận thông báo về hồ sơ / chứng nhận của mình.
        if (entry.leaderId() != null) {
            recipients.addAll(submitterCache.computeIfAbsent(entry.leaderId().toString(), this::findSubmitterAccounts));
        }
        return recipients;
    }

    // createdBy lưu mã huynh trưởng (UUID) hoặc username tùy người gửi.
    private Set<String> findSubmitterAccounts(String submitterActor) {
        boolean leaderId = isUuid(submitterActor);
        String sql = leaderId
                ? "select a.username from account a where a.leader_id = cast(:value as uuid) and a.status = true"
                : "select a.username from account a where a.username = :value and a.status = true";
        return usernames(sql, "value", submitterActor);
    }

    private Set<String> findParishAdmins(UUID parishId) {
        return usernames("""
                select distinct a.username
                from account a
                join account_role ar on ar.username = a.username
                where a.parish_id = cast(:value as uuid)
                  and a.status = true
                  and ar.status = true
                  and ar.role_code = '%s'
                """.formatted(PARISH_ADMIN_ROLE), "value", parishId.toString());
    }

    private Set<String> usernames(String sql, String parameter, String value) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(sql).setParameter(parameter, value).getResultList();
        Set<String> result = new LinkedHashSet<>();
        rows.forEach(row -> result.add(String.valueOf(row)));
        return result;
    }

    private boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không tạo được dữ liệu chi tiết của thông báo", ex);
        }
    }
}
