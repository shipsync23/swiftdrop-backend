package com.swiftdrop.messaging;

import com.swiftdrop.events.OutboxEvent;
import com.swiftdrop.events.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox relay (report 4.4). Every second it takes unpublished events
 * (FOR UPDATE SKIP LOCKED, so several worker replicas never send the same row),
 * publishes them to RabbitMQ, waits for the broker's confirmation, then marks them sent.
 * If RabbitMQ is down the rows stay unpublished and are sent when it comes back.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.worker.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbit;

    public OutboxRelay(OutboxEventRepository outbox, RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outbox.lockUnpublished(BATCH);
        if (batch.isEmpty()) {
            return;
        }
        try {
            rabbit.invoke(ops -> {
                for (OutboxEvent event : batch) {
                    ops.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event.getPayload(), m -> {
                        m.getMessageProperties().setMessageId(String.valueOf(event.getPayload().get("eventId")));
                        m.getMessageProperties().setHeader("x-event-type", event.getEventType());
                        return m;
                    });
                }
                ops.waitForConfirmsOrDie(5000);
                return null;
            });
            Instant now = Instant.now();
            batch.forEach(e -> e.setPublishedAt(now));
            log.info("Outbox relay published {} event(s)", batch.size());
        } catch (Exception e) {
            // Leave them unpublished; the next run retries. The parcel changes are already safe in the DB.
            log.warn("Outbox relay could not publish ({}). Will retry.", e.getClass().getSimpleName());
        }
    }
}
