package com.swiftdrop.merchant.dto;

import com.swiftdrop.pricing.ParcelSize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of POST /api/v1/orders, sent by a merchant's e-commerce platform with its API key. */
public record ApiOrderRequest(
        @NotBlank @Size(min = 5, max = 500) String pickupAddress,
        @NotBlank @Size(min = 5, max = 500) String dropoffAddress,
        @NotNull Integer destZoneId,
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit Sri Lankan number")
        String recipientPhone,
        @NotNull ParcelSize size,
        @NotNull @DecimalMin("0.1") @DecimalMax("30.0") BigDecimal weightKg
) {
}
