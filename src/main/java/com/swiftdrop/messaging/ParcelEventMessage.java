package com.swiftdrop.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** A ParcelStatusChanged event as read by the consumers. */
public record ParcelEventMessage(
        UUID eventId,
        UUID parcelId,
        String trackingNo,
        String oldStatus,
        String newStatus,
        UUID merchantId,
        UUID customerId,
        String timestamp,
        int version,
        Map<String, Object> raw
) {
    @SuppressWarnings("unchecked")
    public static ParcelEventMessage parse(Message message, ObjectMapper mapper) throws IOException {
        Map<String, Object> m = mapper.readValue(message.getBody(), Map.class);
        return new ParcelEventMessage(
                uuid(m.get("eventId")), uuid(m.get("parcelId")), (String) m.get("trackingNo"),
                (String) m.get("oldStatus"), (String) m.get("newStatus"),
                uuid(m.get("merchantId")), uuid(m.get("customerId")), (String) m.get("timestamp"),
                m.get("version") instanceof Number n ? n.intValue() : 0, m);
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
