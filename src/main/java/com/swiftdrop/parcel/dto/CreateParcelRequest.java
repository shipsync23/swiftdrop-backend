package com.swiftdrop.parcel.dto;

import com.swiftdrop.pricing.ParcelSize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /api/v1/parcels (customer confirms a booking).
 * expectedFee is the fee the customer saw; if pricing changed meanwhile, the API returns 409
 * and the customer must confirm the new fee (report 5.1.5 "Recheck the details").
 */
public record CreateParcelRequest(
        @NotBlank @Size(min = 5, max = 500) String pickupAddress,
        @NotBlank @Size(min = 5, max = 500) String dropoffAddress,
        @NotNull Integer destZoneId,
        @NotBlank @Size(max = 150) String recipientName,

        @NotBlank
        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit Sri Lankan number, e.g. 0712345678")
        String recipientPhone,

        @NotNull ParcelSize size,

        @NotNull
        @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
        @DecimalMax(value = "30.0", message = "Maximum parcel weight is 30 kg")
        BigDecimal weightKg,

        @NotNull BigDecimal expectedFee
) {
}
