package phucuong.ht.modules.mobile.push.sender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushSenderTest {

    private static final String URL = "https://exp.host/--/api/v2/push/send";

    private MockRestServiceServer server;
    private ExpoPushSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ExpoPushSender(builder.build());
    }

    private PushSender.Message message(String token) {
        return new PushSender.Message(token, "Tiêu đề", "Nội dung", Map.of("notificationId", "n1"), "default");
    }

    @Test
    void sendsOneBatchAndMapsTicketsInOrder() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].to").value("ExponentPushToken[a]"))
                .andExpect(jsonPath("$[0].title").value("Tiêu đề"))
                .andExpect(jsonPath("$[0].data.notificationId").value("n1"))
                .andExpect(jsonPath("$[0].channelId").value("default"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"status":"ok","id":"t1"},
                          {"status":"error","message":"x","details":{"error":"DeviceNotRegistered"}}
                        ]}""", MediaType.APPLICATION_JSON));

        List<PushSender.Result> results = sender.send(List.of(message("ExponentPushToken[a]"), message("ExponentPushToken[b]")));

        assertEquals(2, results.size());
        assertTrue(results.get(0).ok());
        assertFalse(results.get(0).deviceNotRegistered());
        assertFalse(results.get(1).ok());
        assertTrue(results.get(1).deviceNotRegistered());
        assertEquals("ExponentPushToken[b]", results.get(1).to());
        server.verify();
    }

    @Test
    void splitsMoreThanOneHundredMessagesIntoBatches() {
        List<PushSender.Message> messages = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            messages.add(message("ExponentPushToken[" + i + "]"));
        }
        StringBuilder hundredOk = new StringBuilder("{\"data\":[");
        for (int i = 0; i < 100; i++) {
            hundredOk.append(i == 0 ? "" : ",").append("{\"status\":\"ok\"}");
        }
        hundredOk.append("]}");
        server.expect(requestTo(URL)).andExpect(jsonPath("$.length()").value(100))
                .andRespond(withSuccess(hundredOk.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andExpect(jsonPath("$.length()").value(1))
                .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"}]}", MediaType.APPLICATION_JSON));

        List<PushSender.Result> results = sender.send(messages);

        assertEquals(101, results.size());
        assertTrue(results.stream().allMatch(PushSender.Result::ok));
        server.verify();
    }

    @Test
    void serverErrorDoesNotThrowAndReportsFailures() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        List<PushSender.Result> results = sender.send(List.of(message("ExponentPushToken[a]")));

        assertEquals(1, results.size());
        assertFalse(results.get(0).ok());
        assertNull(results.get(0).error());
        assertFalse(results.get(0).deviceNotRegistered());
    }

    @Test
    void missingTicketIsAFailureNotACrash() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        List<PushSender.Result> results = sender.send(List.of(message("ExponentPushToken[a]")));

        assertFalse(results.get(0).ok());
    }
}
