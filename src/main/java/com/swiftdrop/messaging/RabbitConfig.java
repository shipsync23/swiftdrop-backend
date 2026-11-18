package com.swiftdrop.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ topology (report 4.3):
 *  topic exchange "swiftdrop.events", routing key "parcel.status.changed"
 *  -> notification.queue, webhook.queue, tracking.queue (each consumer gets its own copy)
 *  webhook retry queues with fixed TTLs that dead-letter back to webhook.queue (exponential backoff)
 *  dead-letter queues for messages that still fail after all attempts.
 * Everything is declared automatically when the app connects.
 */
@Configuration
@EnableScheduling
public class RabbitConfig {

    public static final String EXCHANGE = "swiftdrop.events";
    public static final String ROUTING_KEY = "parcel.status.changed";

    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_DLQ = "notification.dlq";
    public static final String WEBHOOK_QUEUE = "webhook.queue";
    public static final String WEBHOOK_DLQ = "webhook.dlq";
    public static final String TRACKING_QUEUE = "tracking.queue";

    /** Retry queue name -> delay in ms. Initial attempt + 5 retries = 6 attempts (report 4.4). */
    public static final Map<String, Integer> WEBHOOK_RETRY_QUEUES = new LinkedHashMap<>();

    static {
        WEBHOOK_RETRY_QUEUES.put("webhook.retry.30s", 30_000);
        WEBHOOK_RETRY_QUEUES.put("webhook.retry.2m", 120_000);
        WEBHOOK_RETRY_QUEUES.put("webhook.retry.10m", 600_000);
        WEBHOOK_RETRY_QUEUES.put("webhook.retry.30m", 1_800_000);
        WEBHOOK_RETRY_QUEUES.put("webhook.retry.2h", 7_200_000);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Declarables swiftDropTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);

        Queue notificationDlq = QueueBuilder.durable(NOTIFICATION_DLQ).build();
        Queue webhookDlq = QueueBuilder.durable(WEBHOOK_DLQ).build();

        Queue notification = QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange("").deadLetterRoutingKey(NOTIFICATION_DLQ).build();
        Queue webhook = QueueBuilder.durable(WEBHOOK_QUEUE)
                .deadLetterExchange("").deadLetterRoutingKey(WEBHOOK_DLQ).build();
        Queue tracking = QueueBuilder.durable(TRACKING_QUEUE).build();

        List<Declarable> all = new ArrayList<>(List.of(exchange, notificationDlq, webhookDlq,
                notification, webhook, tracking,
                bind(notification, exchange), bind(webhook, exchange), bind(tracking, exchange)));

        // Each retry queue holds a message for its TTL, then dead-letters it back to webhook.queue.
        WEBHOOK_RETRY_QUEUES.forEach((name, ttl) -> all.add(QueueBuilder.durable(name)
                .ttl(ttl).deadLetterExchange("").deadLetterRoutingKey(WEBHOOK_QUEUE).build()));

        return new Declarables(all);
    }

    private static Binding bind(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }
}
