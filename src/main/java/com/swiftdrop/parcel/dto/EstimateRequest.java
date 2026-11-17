package com.swiftdrop.parcel.dto;

import com.swiftdrop.pricing.ParcelSize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Body of POST /api/v1/parcels/estimate. */
public record EstimateRequest(
        @NotNull Integer destZoneId,

        @NotNull
        @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
        @DecimalMax(value = "30.0", message = "Maximum parcel weight is 30 kg")
        BigDecimal weightKg,

        @NotNull ParcelSize size
) {
}
