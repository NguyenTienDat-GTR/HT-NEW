package phucuong.ht.modules.mobile.push.sender;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi thông báo qua Expo Push Service (https://docs.expo.dev/push-notifications/sending-notifications/):
 * một lần gọi tối đa 100 tin, có thời gian chờ ngắn. Mọi lỗi được nuốt và ghi log để không ảnh hưởng luồng gọi.
 */
@Slf4j
@Component
public class ExpoPushSender implements PushSender {

    static final int BATCH_SIZE = 100;

    private final RestClient restClient;

    @Autowired
    public ExpoPushSender(
            @Value("${app.mobile.push.expo-url:https://exp.host/--/api/v2/push/send}") String url,
            @Value("${app.mobile.push.timeout-ms:5000}") long timeoutMs
    ) {
        this(buildClient(url, timeoutMs));
    }

    ExpoPushSender(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildClient(String url, long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public List<Result> send(List<Message> messages) {
        List<Result> results = new ArrayList<>(messages.size());
        for (int from = 0; from < messages.size(); from += BATCH_SIZE) {
            List<Message> batch = messages.subList(from, Math.min(from + BATCH_SIZE, messages.size()));
            results.addAll(sendBatch(batch));
        }
        return results;
    }

    private List<Result> sendBatch(List<Message> batch) {
        try {
            JsonNode response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(batch.stream().map(ExpoPushSender::toPayload).toList())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = response == null ? null : response.get("data");
            List<Result> results = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                JsonNode ticket = data != null && data.isArray() && i < data.size() ? data.get(i) : null;
                results.add(toResult(batch.get(i), ticket));
            }
            return results;
        } catch (RuntimeException exception) {
            log.warn("Gửi thông báo đẩy qua Expo thất bại ({} tin): {}", batch.size(), exception.toString());
            return batch.stream().map(message -> new Result(message.to(), false, null)).toList();
        }
    }

    private static Map<String, Object> toPayload(Message message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", message.to());
        payload.put("title", message.title());
        payload.put("body", message.body());
        payload.put("sound", "default");
        if (message.channelId() != null) {
            payload.put("channelId", message.channelId());
        }
        if (message.data() != null && !message.data().isEmpty()) {
            payload.put("data", message.data());
        }
        return payload;
    }

    private static Result toResult(Message message, JsonNode ticket) {
        if (ticket == null) {
            return new Result(message.to(), false, null);
        }
        if ("ok".equals(ticket.path("status").asText())) {
            return new Result(message.to(), true, null);
        }
        String error = ticket.path("details").path("error").asText(null);
        return new Result(message.to(), false, error == null || error.isBlank() ? null : error);
    }
}
