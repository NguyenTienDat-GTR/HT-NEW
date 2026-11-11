package phucuong.ht.modules.leader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.excel.ExcelWorkbookReader;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phát hiện hồ sơ huynh trưởng trùng khi import Excel (hồ sơ huynh trưởng và danh sách tham dự khóa huấn luyện).
 * <p>
 * "Cùng danh tính" là trùng cả tên thánh, họ và tên, ngày sinh và giáo xứ (tên không phân biệt hoa/thường và dấu). Với số điện thoại của dòng:
 * <ul>
 *   <li>số điện thoại đã có ở một huynh trưởng cùng danh tính: coi là chính người đó ({@link Kind#SAME_PERSON});</li>
 *   <li>số điện thoại đã có ở một huynh trưởng khác danh tính: lỗi trùng ({@link Kind#PHONE_CONFLICT});</li>
 *   <li>số điện thoại chưa có nhưng có huynh trưởng cùng danh tính: nghi trùng, cần người dùng xác nhận ({@link Kind#POSSIBLE_DUPLICATE});</li>
 *   <li>còn lại là người mới ({@link Kind#NEW}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LeaderDuplicateMatcher {

    private final LeaderRepository leaderRepository;

    public enum Kind {
        NEW, SAME_PERSON, POSSIBLE_DUPLICATE, PHONE_CONFLICT
    }

    /** Danh tính của một dòng Excel đã kiểm tra hợp lệ (giáo xứ đã xác định được). */
    public record Identity(String holyName, String fullName, LocalDate birthDate, UUID parishId) {
    }

    /** {@code leader}: huynh trưởng khớp (SAME_PERSON) hoặc giữ số điện thoại (PHONE_CONFLICT); {@code candidates}: các hồ sơ nghi trùng (POSSIBLE_DUPLICATE). */
    public record Match(Kind kind, Leader leader, List<Leader> candidates) {

        public Leader candidate(UUID id) {
            return id == null ? null : candidates.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
        }
    }

    /** Hồ sơ đã có trong hệ thống liên quan tới các dòng của một file (tra cứu một lần cho cả file). */
    public static final class Index {
        private final Map<String, Leader> byPhone = new HashMap<>();
        private final Map<String, List<Leader>> byIdentity = new HashMap<>();
    }

    public Index load(Collection<String> phones, Collection<LocalDate> birthDates) {
        Index index = new Index();
        for (Leader leader : leaderRepository.findAllByPhoneNumbers(phones)) {
            index.byPhone.put(leader.getPhoneNumber(), leader);
        }
        for (Leader leader : leaderRepository.findAllByBirthDates(birthDates)) {
            index.byIdentity.computeIfAbsent(key(leader.getHolyName(), fullNameOf(leader), leader.getBirthDate(), leader.getParish().getId()),
                    ignored -> new ArrayList<>()).add(leader);
        }
        return index;
    }

    public Match classify(Identity identity, String phone, Index index) {
        List<Leader> sameIdentity = index.byIdentity.getOrDefault(
                key(identity.holyName(), identity.fullName(), identity.birthDate(), identity.parishId()), List.of());
        Leader phoneOwner = phone == null ? null : index.byPhone.get(phone);
        if (phoneOwner != null) {
            boolean same = sameIdentity.stream().anyMatch(item -> item.getId().equals(phoneOwner.getId()));
            return new Match(same ? Kind.SAME_PERSON : Kind.PHONE_CONFLICT, phoneOwner, List.of());
        }
        if (!sameIdentity.isEmpty()) {
            return new Match(Kind.POSSIBLE_DUPLICATE, null, sameIdentity);
        }
        return new Match(Kind.NEW, null, List.of());
    }

    /** Số điện thoại che bớt, chỉ giữ 3 số cuối. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.length() <= 3 ? phone : "*".repeat(phone.length() - 3) + phone.substring(phone.length() - 3);
    }

    private static String fullNameOf(Leader leader) {
        String last = leader.getLastName();
        return (last == null || last.isBlank() ? "" : last + " ") + leader.getFirstName();
    }

    private static String key(String holyName, String fullName, LocalDate birthDate, UUID parishId) {
        return ExcelWorkbookReader.normalize(holyName) + "|" + ExcelWorkbookReader.normalize(fullName) + "|" + birthDate + "|" + parishId;
    }
}
