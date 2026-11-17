package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.StatusHistory;

import java.time.Instant;

public record StatusHistoryResponse(
        String oldStatus,
        String newStatus,
        String note,
        Instant changedAt
) {
    public static StatusHistoryResponse from(StatusHistory h) {
        return new StatusHistoryResponse(
                h.getOldStatus() == null ? null : h.getOldStatus().name(),
                h.getNewStatus().name(), h.getNote(), h.getChangedAt());
    }
}
