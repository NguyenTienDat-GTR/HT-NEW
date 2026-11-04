package phucuong.ht.common.layoutconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lịch sử phiên bản của cấu hình chứng nhận / thẻ: giữ tối đa {@value #MAX_VERSIONS} bản gần nhất cho mỗi cấu hình. */
@Service
@RequiredArgsConstructor
public class LayoutConfigVersionService {

    public static final int MAX_VERSIONS = 20;
    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String BADGE = "BADGE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public record Version(UUID id, int versionNo, OffsetDateTime createdAt, String createdBy, Map<String, Object> snapshot) {
    }

    /** Ghi lại bản của cấu hình trước khi nó bị đổi. */
    @Transactional
    public void record(String configType, UUID configId, Map<String, Object> snapshot, String actor) {
        Integer last = jdbc.queryForObject(
                "select coalesce(max(version_no), 0) from layout_config_version where config_type = ? and config_id = ?",
                Integer.class, configType, configId);
        int next = (last == null ? 0 : last) + 1;
        jdbc.update("insert into layout_config_version (id, config_type, config_id, version_no, snapshot, created_at, created_by) values (?, ?, ?, ?, ?::jsonb, now(), ?)",
                UUID.randomUUID(), configType, configId, next, toJson(snapshot), actor);
        jdbc.update("delete from layout_config_version where config_type = ? and config_id = ? and version_no <= ?",
                configType, configId, next - MAX_VERSIONS);
    }

    @Transactional(readOnly = true)
    public List<Version> list(String configType, UUID configId) {
        return jdbc.query(
                "select id, version_no, created_at, created_by, snapshot::text as snapshot from layout_config_version where config_type = ? and config_id = ? order by version_no desc",
                (rs, row) -> new Version(
                        rs.getObject("id", UUID.class),
                        rs.getInt("version_no"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("created_by"),
                        fromJson(rs.getString("snapshot"))),
                configType, configId);
    }

    @Transactional
    public void deleteAll(String configType, UUID configId) {
        jdbc.update("delete from layout_config_version where config_type = ? and config_id = ?", configType, configId);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được lịch sử phiên bản", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được lịch sử phiên bản", e);
        }
    }
}
