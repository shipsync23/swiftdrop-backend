package com.swiftdrop.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.messaging.IdempotencyGuard;
import com.swiftdrop.messaging.ParcelEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Idempotent consumer (report 4.4): a duplicate event must not send a second notification. */
class NotificationConsumerTest {

    private final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    private final NotificationService service = mock(NotificationService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationConsumer consumer = new NotificationConsumer(guard, service, mapper);

    @Test
    void firstDeliverySendsTheNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(guard.firstTime(eventId, NotificationConsumer.CONSUMER)).thenReturn(true);

        consumer.onEvent(message(eventId));

        verify(service, times(1)).notifyFor(any(ParcelEventMessage.class));
    }

    @Test
    void duplicateDeliveryIsSkipped() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(guard.firstTime(eq(eventId), any())).thenReturn(false);

        consumer.onEvent(message(eventId));

        verify(service, never()).notifyFor(any());
    }

    @Test
    void messageIsParsedIntoTheEventContract() throws Exception {
        UUID eventId = UUID.randomUUID();
        ParcelEventMessage parsed = ParcelEventMessage.parse(message(eventId), mapper);
        assertThat(parsed.eventId()).isEqualTo(eventId);
        assertThat(parsed.newStatus()).isEqualTo("IN_TRANSIT");
        assertThat(parsed.version()).isEqualTo(2);
    }

    @Test
    void smsLogsMaskThePhoneNumber() {
        assertThat(MockSmsNotifier.mask("0712345678")).isEqualTo("071****678");
        assertThat(MockSmsNotifier.mask(null)).isEqualTo("****");
    }

    private Message message(UUID eventId) throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", eventId.toString(),
                "parcelId", UUID.randomUUID().toString(),
                "trackingNo", "SDTEST1234",
                "oldStatus", "PICKED_UP",
                "newStatus", "IN_TRANSIT",
                "customerId", UUID.randomUUID().toString(),
                "timestamp", "2026-11-18T10:00:00Z",
                "version", 2);
        return new Message(mapper.writeValueAsBytes(payload), new MessageProperties());
    }
}
