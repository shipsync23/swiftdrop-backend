package com.swiftdrop.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single domain event of the system (report 4.3).
 * Parcel creation is also published with oldStatus = null and newStatus = CREATED.
 * Contains no addresses or phone numbers: consumers load what they need from the database.
 */
public record ParcelStatusChangedEvent(
        UUID eventId,
        UUID parcelId,
        String trackingNo,
        String oldStatus,
        String newStatus,
        UUID changedBy,
        UUID merchantId,
        UUID customerId,
        String timestamp,
        int version
) {
    public static final String TYPE = "ParcelStatusChanged";

    /** JSON-friendly map stored in outbox_events.payload. */
    public Map<String, Object> toPayload() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId.toString());
        map.put("parcelId", parcelId.toString());
        map.put("trackingNo", trackingNo);
        map.put("oldStatus", oldStatus);
        map.put("newStatus", newStatus);
        map.put("changedBy", changedBy == null ? null : changedBy.toString());
        map.put("merchantId", merchantId == null ? null : merchantId.toString());
        map.put("customerId", customerId == null ? null : customerId.toString());
        map.put("timestamp", timestamp);
        map.put("version", version);
        return map;
    }
}
