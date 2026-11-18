package com.swiftdrop.webhook;

import com.swiftdrop.messaging.IdempotencyGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Records each attempt; a successful one also marks the event as processed for this consumer. */
@Service
public class WebhookDeliveryService {

    static final String CONSUMER = "webhook";

    private final WebhookDeliveryRepository deliveries;
    private final IdempotencyGuard guard;

    public WebhookDeliveryService(WebhookDeliveryRepository deliveries, IdempotencyGuard guard) {
        this.deliveries = deliveries;
        this.guard = guard;
    }

    @Transactional
    public void record(UUID merchantId, UUID parcelId, UUID eventId, int attempt,
                       WebhookSender.Result result, String outcome) {
        WebhookDelivery d = new WebhookDelivery();
        d.setMerchantId(merchantId);
        d.setParcelId(parcelId);
        d.setEventId(eventId);
        d.setAttemptNo(attempt);
        d.setHttpStatus(result.httpStatus());
        d.setResult(outcome);
        d.setErrorMessage(result.error());
        deliveries.save(d);
        if ("SUCCESS".equals(outcome)) {
            guard.firstTime(eventId, CONSUMER);
        }
    }

    public boolean alreadyDelivered(UUID eventId) {
        return guard.alreadyProcessed(eventId, CONSUMER);
    }
}
