package com.swiftdrop.courier;

import com.swiftdrop.parcel.ParcelStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * What a courier sends when updating a parcel.
 * expectedVersion (optional) is the parcel version the courier's screen was showing;
 * if someone changed the parcel since, the update is rejected with 409.
 */
public record StatusUpdateCommand(
        ParcelStatus status,
        String note,
        String recipientName,
        String failureReason,
        MultipartFile photo,
        Integer expectedVersion
) {
}
