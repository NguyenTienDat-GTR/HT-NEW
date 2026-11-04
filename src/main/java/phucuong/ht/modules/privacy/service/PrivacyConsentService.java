package phucuong.ht.modules.privacy.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.privacy.model.PrivacyConsentSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Ghi và tra cứu bằng chứng đồng ý xử lý dữ liệu cá nhân. */
@Service
@RequiredArgsConstructor
public class PrivacyConsentService {

    static final String PURPOSES =
            "Quản lý hồ sơ huynh trưởng, đào tạo và cấp chứng nhận";

    private final JdbcTemplate jdbc;

    @Value("${app.privacy.policy-version:1.0}")
    private String policyVersion;

    public record Status(String policyVersion, boolean consented, String consentedVersion, OffsetDateTime consentedAt) {
    }

    public String policyVersion() {
        return policyVersion;
    }

    @Transactional(readOnly = true)
    public Status status(Authentication authentication) {
        List<Status> rows = jdbc.query(
                "select policy_version, consented_at from privacy_consent"
                        + " where consent_type = 'SELF' and username = ? and withdrawn_at is null"
                        + " order by consented_at desc limit 1",
                (rs, i) -> new Status(policyVersion, policyVersion.equals(rs.getString("policy_version")),
                        rs.getString("policy_version"), rs.getObject("consented_at", OffsetDateTime.class)),
                authentication.getName());
        return rows.isEmpty() ? new Status(policyVersion, false, null, null) : rows.get(0);
    }

    /** Người dùng tự đồng ý cho dữ liệu của chính mình. */
    @Transactional
    public void recordSelf(Authentication authentication, PrivacyConsentSource source) {
        insert("SELF", authentication.getName(), null, authentication.getName(), source);
    }

    /** Quản trị viên xác nhận người có dữ liệu đã đồng ý. subjectLeaderId có thể null (import hàng loạt, tài khoản, đơn vị). */
    @Transactional
    public void recordAttestation(Authentication authentication, UUID subjectLeaderId, PrivacyConsentSource source) {
        insert("ADMIN_ATTESTATION", null, subjectLeaderId, authentication.getName(), source);
    }

    /** Rút đồng ý (Điều 10 Luật 91/2025): đánh dấu mọi đồng ý tự thân còn hiệu lực là đã rút. Không tự xóa dữ liệu. */
    @Transactional
    public int withdraw(Authentication authentication) {
        return jdbc.update(
                "update privacy_consent set withdrawn_at = now() where consent_type = 'SELF' and username = ? and withdrawn_at is null",
                authentication.getName());
    }

    /** Dùng cho endpoint nhận file (không đi qua validation của DTO): thiếu xác nhận thì từ chối. */
    public void requireConfirmed(Boolean confirmed) {
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Vui lòng xác nhận người có thông tin đã được thông báo và đồng ý");
        }
    }

    private void insert(String type, String username, UUID subjectLeaderId, String recordedBy, PrivacyConsentSource source) {
        HttpServletRequest request = currentRequest();
        jdbc.update(
                "insert into privacy_consent (id, consent_type, username, subject_leader_id, recorded_by, purposes,"
                        + " policy_version, source, ip_address, user_agent, consented_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), type, username, subjectLeaderId, recordedBy, PURPOSES, policyVersion, source.name(),
                request == null ? null : clientIp(request),
                request == null ? null : truncate(request.getHeader("User-Agent"), 500));
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return truncate(ip, 64);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
