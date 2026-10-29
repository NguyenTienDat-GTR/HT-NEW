package phucuong.ht.common.excel.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import phucuong.ht.common.excel.AvatarPlan;
import phucuong.ht.common.excel.AvatarWarning;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.config.CacheNames;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.service.MediaAssetService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Xử lý ảnh sau khi transaction import dữ liệu đã commit. */
@Component
@RequiredArgsConstructor
public class ExcelAvatarProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExcelAvatarProcessor.class);
    // Hồ sơ vừa có ảnh phải hiện ngay: xóa cache huynh trưởng sau mỗi ngần này ảnh, không đợi hết lô.
    private static final int CACHE_CLEAR_EVERY = 25;

    private final ExcelJobRepository repository;
    private final ObjectMapper objectMapper;
    private final MediaProperties mediaProperties;
    private final MediaAssetService mediaAssetService;
    private final LeaderRepository leaderRepository;
    private final CacheManager cacheManager;

    public void process(ExcelJobRepository.Row row, int leaseSeconds) {
        List<AvatarPlan> plans = readPlans(row.avatarPlan());
        ObjectNode result = readResult(row.resultJson());
        ArrayNode warnings = result.withArray("avatarWarnings");
        int processed = Math.min(result.path("avatarProcessed").asInt(0), plans.size());
        int succeeded = result.path("avatarSucceeded").asInt(0);
        result.put("avatarTotal", plans.size());

        if (row.imagesPath() == null) {
            for (int index = processed; index < plans.size(); index++) {
                addWarning(warnings, plans.get(index), "File ZIP ảnh không còn tồn tại");
            }
            processed = plans.size();
        } else {
            try (AvatarZip images = new AvatarZip(Path.of(row.imagesPath()), mediaProperties.getMaxAvatarBytes())) {
                for (int index = processed; index < plans.size(); index++) {
                    AvatarPlan plan = plans.get(index);
                    try {
                        // Có thể là lần chạy lại sau khi ảnh đã gắn nhưng tiến độ chưa kịp lưu.
                        if (leaderRepository.hasImageAsset(plan.leaderId())) {
                            succeeded++;
                        } else {
                            byte[] bytes = images.read(plan.fileName());
                            UUID assetId = mediaAssetService.createActive(bytes, MediaCategory.LEADER_AVATAR, plan.dioceseId(), row.owner()).getId();
                            if (leaderRepository.setImageAssetIfAbsent(plan.leaderId(), assetId) == 1) {
                                succeeded++;
                            } else {
                                mediaAssetService.release(assetId);
                                addWarning(warnings, plan, "Hồ sơ đã có ảnh, giữ ảnh hiện tại");
                            }
                        }
                    } catch (IOException | RuntimeException exception) {
                        log.warn("Không gắn được ảnh {} cho hồ sơ {} của job {}", plan.fileName(), plan.leaderId(), row.id(), exception);
                        addWarning(warnings, plan, reason(exception));
                    }
                    processed++;
                    writeProgress(result, processed, plans.size(), succeeded);
                    repository.touchAvatar(row.id(), json(result), leaseSeconds);
                    if (processed % CACHE_CLEAR_EVERY == 0) {
                        clearLeaderCaches();
                    }
                }
            } catch (IOException | RuntimeException exception) {
                log.warn("Không đọc được ZIP ảnh của job {}", row.id(), exception);
                for (int index = processed; index < plans.size(); index++) {
                    addWarning(warnings, plans.get(index), reason(exception));
                }
                processed = plans.size();
            }
        }

        writeProgress(result, processed, plans.size(), succeeded);
        boolean finished = repository.finishAvatar(row.id(), json(result)) == 1;
        // Luôn xóa cache, kể cả khi job đã bị đổi trạng thái từ nơi khác: ảnh đã gắn thì danh sách phải thấy ngay.
        clearLeaderCaches();
        if (finished) {
            ExcelJobService.deleteQuietly(row.imagesPath());
        }
    }

    private List<AvatarPlan> readPlans(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không đọc được kế hoạch ảnh", exception);
        }
    }

    private ObjectNode readResult(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : (ObjectNode) objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private void writeProgress(ObjectNode result, int processed, int total, int succeeded) {
        result.put("avatarProcessed", processed);
        result.put("avatarTotal", total);
        result.put("avatarSucceeded", succeeded);
    }

    private void addWarning(ArrayNode warnings, AvatarPlan plan, String reason) {
        warnings.add(objectMapper.valueToTree(new AvatarWarning(plan.row(), plan.fullName(), plan.fileName(), reason)));
    }

    private String reason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Không xử lý được ảnh";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void clearLeaderCaches() {
        for (String name : List.of(CacheNames.LEADER_LIST, CacheNames.LEADER_DETAIL)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
