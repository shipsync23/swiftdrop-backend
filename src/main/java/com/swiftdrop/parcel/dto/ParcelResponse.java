package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.Parcel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Full parcel details: only for the owner, the assigned courier and staff (report 6.4). */
public record ParcelResponse(
        UUID id,
        String trackingNumber,
        String status,
        String pickupAddress,
        String dropoffAddress,
        Integer destZoneId,
        String destZone,
        String recipientName,
        String recipientPhone,
        String size,
        BigDecimal weightKg,
        BigDecimal fee,
        String source,
        Instant createdAt,
        Instant updatedAt
) {
    public static ParcelResponse from(Parcel p) {
        return new ParcelResponse(p.getId(), p.getTrackingNumber(), p.getStatus().name(),
                p.getPickupAddress(), p.getDropoffAddress(),
                p.getDestZone().getId(), p.getDestZone().getName(),
                p.getRecipientName(), p.getRecipientPhone(),
                p.getSize().name(), p.getWeightKg(), p.getFee(),
                p.getMerchantId() != null ? "MERCHANT_API" : "CUSTOMER",
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
