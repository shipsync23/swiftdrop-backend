package com.swiftdrop.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.security.SecretCipher;
import com.swiftdrop.merchant.Merchant;
import com.swiftdrop.merchant.MerchantRepository;
import com.swiftdrop.messaging.ParcelEventMessage;
import com.swiftdrop.messaging.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Webhook dispatcher (report 4.4 Reliability):
 *  attempt 1 from webhook.queue; on failure the message goes to webhook.retry.30s, .2m, .10m, .30m, .2h
 *  (each TTL queue dead-letters back here), so 6 attempts in total with exponential backoff.
 *  After the last failure it is moved to webhook.dlq. Every attempt is logged in webhook_deliveries.
 *  A failed webhook never undoes the parcel change.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookConsumer.class);
    static final String ATTEMPT_HEADER = "x-webhook-attempt";
    private static final List<String> RETRY_QUEUES = new ArrayList<>(RabbitConfig.WEBHOOK_RETRY_QUEUES.keySet());

    private final MerchantRepository merchants;
    private final SecretCipher cipher;
    private final WebhookSender sender;
    private final WebhookDeliveryService deliveryService;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;

    public WebhookConsumer(MerchantRepository merchants, SecretCipher cipher, WebhookSender sender,
                           WebhookDeliveryService deliveryService, RabbitTemplate rabbit, ObjectMapper objectMapper) {
        this.merchants = merchants;
        this.cipher = cipher;
        this.sender = sender;
        this.deliveryService = deliveryService;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.WEBHOOK_QUEUE)
    public void onEvent(Message message) throws IOException {
        ParcelEventMessage event = ParcelEventMessage.parse(message, objectMapper);
        if (event.merchantId() == null) {
            return;   // customer booking: no merchant to notify
        }
        if (deliveryService.alreadyDelivered(event.eventId())) {
            return;   // duplicate
        }
        Merchant merchant = merchants.findById(event.merchantId()).orElse(null);
        if (merchant == null || merchant.getWebhookUrl() == null || merchant.getWebhookSecretEnc() == null) {
            return;   // merchant has not configured a webhook
        }

        int attempt = attemptOf(message);
        byte[] body = objectMapper.writeValueAsBytes(webhookBody(event));
        WebhookSender.Result result = sender.send(merchant.getWebhookUrl(),
                cipher.decrypt(merchant.getWebhookSecretEnc()), body, event.eventId(), attempt);

        if (result.success()) {
            deliveryService.record(merchant.getId(), event.parcelId(), event.eventId(), attempt, result, "SUCCESS");
            return;
        }

        int retryIndex = attempt - 1;   // attempt 1 failed -> first retry queue
        if (retryIndex < RETRY_QUEUES.size()) {
            deliveryService.record(merchant.getId(), event.parcelId(), event.eventId(), attempt, result, "RETRY");
            republish(message, RETRY_QUEUES.get(retryIndex), attempt + 1);
            log.info("Webhook for {} failed (attempt {}), retry via {}", event.trackingNo(), attempt,
                    RETRY_QUEUES.get(retryIndex));
        } else {
            deliveryService.record(merchant.getId(), event.parcelId(), event.eventId(), attempt, result, "DEAD");
            republish(message, RabbitConfig.WEBHOOK_DLQ, attempt);
            log.warn("Webhook for {} moved to {} after {} attempts", event.trackingNo(), RabbitConfig.WEBHOOK_DLQ,
                    attempt);
        }
    }

    /** What the merchant receives: no customer data, only the order status. */
    private static Map<String, Object> webhookBody(ParcelEventMessage e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", e.eventId().toString());
        body.put("eventType", "ParcelStatusChanged");
        body.put("trackingNumber", e.trackingNo());
        body.put("parcelId", e.parcelId().toString());
        body.put("oldStatus", e.oldStatus());
        body.put("newStatus", e.newStatus());
        body.put("timestamp", e.timestamp());
        return body;
    }

    private static int attemptOf(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(ATTEMPT_HEADER);
        return value instanceof Number n ? n.intValue() : 1;
    }

    /** Send through the default exchange straight to a queue, keeping the body and other headers. */
    private void republish(Message original, String queue, int nextAttempt) {
        Message copy = MessageBuilder.fromMessage(original)
                .setHeader(ATTEMPT_HEADER, nextAttempt)
                .build();
        copy.getMessageProperties().getHeaders().remove("x-death");
        rabbit.send("", queue, copy);
    }
}
