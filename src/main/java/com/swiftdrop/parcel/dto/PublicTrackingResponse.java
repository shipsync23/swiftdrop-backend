package com.swiftdrop.parcel.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the public tracking page shows: status only. No names, addresses or phone numbers
 * (report 2.6 "Public visitor ... cannot see private details").
 */
public record PublicTrackingResponse(
        String trackingNumber,
        String status,
        String destZone,
        Instant lastUpdated,
        List<Step> history
) {
    public record Step(String status, Instant at) {
    }
}
