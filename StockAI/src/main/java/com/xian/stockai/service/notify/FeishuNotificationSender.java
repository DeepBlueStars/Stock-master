package com.xian.stockai.service.notify;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xian.stockai.po.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * @author xian
 * @description 飞书自定义机器人推送
 */
@Component
@ConditionalOnProperty(prefix = "feishu", name = "enabled", havingValue = "true")
@Slf4j
public class FeishuNotificationSender implements NotificationSender {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String secret;

    public FeishuNotificationSender(RestClient.Builder restClientBuilder,
                                    @Value("${feishu.webhook-url:}") String webhookUrl,
                                    @Value("${feishu.secret:}") String secret) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
        this.secret = secret;
    }

    @Override
    public void send(Notification notification) {
        try {
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                log.warn("飞书 webhook-url 未配置，跳过推送");
                return;
            }
            Map<String, Object> payload = buildPayload(notification);
            String response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .body(String.class);
            JSONObject node = JSON.parseObject(response);
            Integer code = node.getInteger("code");
            if (code == null || code != 0) {
                log.warn("飞书推送失败: code={}, msg={}", code, node.getString("msg"));
            } else {
                log.debug("飞书推送成功: userId={}, symbol={}", notification.getUserId(), notification.getSymbol());
            }
        } catch (Exception e) {
            log.error("飞书推送异常: userId={}, symbol={}", notification.getUserId(), notification.getSymbol(), e);
        }
    }

    Map<String, Object> buildPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msg_type", "text");
        Map<String, String> content = new HashMap<>();
        content.put("text", notification.getContent());
        payload.put("content", content);
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis() / 1000;
            payload.put("timestamp", timestamp);
            payload.put("sign", sign(timestamp, secret));
        }
        return payload;
    }

    String sign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new IllegalStateException("飞书加签失败", e);
        }
    }
}
