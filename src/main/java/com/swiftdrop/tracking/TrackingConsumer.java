package com.swiftdrop.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.messaging.ParcelEventMessage;
import com.swiftdrop.messaging.RabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Consumes tracking.queue and pushes the new status to open tracking pages (no personal data). */
@Component
public class TrackingConsumer {

    private final TrackingGateway gateway;
    private final ObjectMapper objectMapper;

    public TrackingConsumer(TrackingGateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.TRACKING_QUEUE)
    public void onEvent(Message message) throws IOException {
        ParcelEventMessage e = ParcelEventMessage.parse(message, objectMapper);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("trackingNumber", e.trackingNo());
        update.put("status", e.newStatus());
        update.put("previousStatus", e.oldStatus());
        update.put("at", e.timestamp());
        gateway.push(e.trackingNo(), e.version(), update);
    }
}
