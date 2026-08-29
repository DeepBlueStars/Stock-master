package com.xian.stockai.service.notify;

import com.xian.stockai.po.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class FeishuNotificationSenderTest {

    private static final String WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/test";

    private FeishuNotificationSender sender(String secret) {
        return new FeishuNotificationSender(RestClient.builder(), WEBHOOK_URL, secret);
    }

    private Notification notification() {
        Notification n = new Notification();
        n.setUserId("u1");
        n.setSymbol("000001");
        n.setStockName("平安银行");
        n.setContent("【止盈提醒】平安银行(000001) 最新收盘价 15.00 已触发止盈");
        return n;
    }

    @Test
    void buildPayloadUsesTextMessageWithoutSignature() {
        Map<String, Object> payload = sender("").buildPayload(notification());
        assertEquals("text", payload.get("msg_type"));
        Map<?, ?> content = (Map<?, ?>) payload.get("content");
        assertEquals("【止盈提醒】平安银行(000001) 最新收盘价 15.00 已触发止盈", content.get("text"));
        assertNull(payload.get("timestamp"));
        assertNull(payload.get("sign"));
    }

    @Test
    void buildPayloadAddsSignatureWhenSecretPresent() {
        Map<String, Object> payload = sender("test-secret").buildPayload(notification());
        assertNotNull(payload.get("timestamp"));
        assertNotNull(payload.get("sign"));
        assertFalse(payload.get("sign").toString().isEmpty());
    }

    @Test
    void signMatchesHmacSha256Base64() {
        long timestamp = 1700000000L;
        String expected = hmacSha256Base64(timestamp + "\n" + "test-secret", "test-secret");
        assertEquals(expected, sender("test-secret").sign(timestamp, "test-secret"));
    }

    @Test
    void sendSwallowsHttpError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());
        FeishuNotificationSender sender = new FeishuNotificationSender(builder, WEBHOOK_URL, "");
        assertDoesNotThrow(() -> sender.send(notification()));
    }

    private String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
