package com.swiftdrop.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.messaging.IdempotencyGuard;
import com.swiftdrop.messaging.ParcelEventMessage;
import com.swiftdrop.messaging.RabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Consumes notification.queue. The processed_events row and the notification are saved in one
 * transaction, so a redelivered event is skipped (idempotent consumer, report 4.4).
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    static final String CONSUMER = "notification";

    private final IdempotencyGuard guard;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(IdempotencyGuard guard, NotificationService notificationService,
                                ObjectMapper objectMapper) {
        this.guard = guard;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    @Transactional
    public void onEvent(Message message) throws IOException {
        ParcelEventMessage event = ParcelEventMessage.parse(message, objectMapper);
        if (!guard.firstTime(event.eventId(), CONSUMER)) {
            return;   // duplicate delivery
        }
        notificationService.notifyFor(event);
    }
}
