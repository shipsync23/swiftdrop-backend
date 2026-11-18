package com.swiftdrop.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.swiftdrop.messaging.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The asynchronous integration flow (report 4, 5.3.4):
 * status change -> outbox row (same transaction) -> relay -> RabbitMQ -> notification consumer -> email.
 * Also checks that a duplicate event does not send a second notification (idempotent consumer).
 */
class EventFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitTemplate rabbit;

    @Test
    void statusChangeTriggersNotificationThroughTheBroker() throws Exception {
        String parcelId = createParcel(login("customer@swiftdrop.lk")).get("id").asText();
        String dispatcher = login("dispatcher@swiftdrop.lk");
        assign(dispatcher, parcelId, courierId(dispatcher, "Kamal Perera"));

        mvc.perform(multipart("/api/v1/courier/parcels/{id}/status", parcelId)
                        .param("status", "PICKED_UP")
                        .header("Authorization", bearer(login("courier1@swiftdrop.lk"))))
                .andExpect(status().isOk());

        UUID parcel = UUID.fromString(parcelId);

        // 1. The event was stored in the outbox in the same transaction as the update
        Integer outboxRows = jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ? and payload->>'newStatus' = 'PICKED_UP'",
                Integer.class, parcel);
        assertThat(outboxRows).isEqualTo(1);

        // 2. The relay published it to RabbitMQ
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ? and published_at is null",
                Integer.class, parcel)).isZero());

        // 3. The notification consumer sent and recorded the email
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from notifications where parcel_id = ? and channel = 'EMAIL' and message like '%picked up%'",
                Integer.class, parcel)).isEqualTo(1));
        verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
    }

    @Test
    void duplicateEventIsProcessedOnlyOnce() throws Exception {
        String parcelId = createParcel(login("customer@swiftdrop.lk")).get("id").asText();
        UUID parcel = UUID.fromString(parcelId);

        // Wait until the CREATED event has been handled once
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from notifications where parcel_id = ?", Integer.class, parcel)).isPositive());
        int before = jdbc.queryForObject("select count(*) from notifications where parcel_id = ?", Integer.class, parcel);

        // Re-send exactly the same event (same eventId), as an at-least-once broker might
        String payloadJson = jdbc.queryForObject(
                "select payload::text from outbox_events where aggregate_id = ?", String.class, parcel);
        Map<String, Object> payload = json.readValue(payloadJson, new TypeReference<>() { });
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, payload);

        // Give the consumer time to receive it; the count must not change
        await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select count(*) from notifications where parcel_id = ?",
                        Integer.class, parcel)).isEqualTo(before));
        assertThat(jdbc.queryForObject(
                "select count(*) from processed_events where event_id = ?::uuid and consumer_name = 'notification'",
                Integer.class, payload.get("eventId"))).isEqualTo(1);
    }
}
