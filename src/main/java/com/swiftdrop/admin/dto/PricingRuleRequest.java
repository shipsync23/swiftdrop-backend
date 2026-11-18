package com.swiftdrop.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PricingRuleRequest(
        @NotNull Integer zoneId,
        @NotNull @DecimalMin("0.0") @DecimalMax("30.0") BigDecimal minWeightKg,
        @NotNull @DecimalMin("0.1") @DecimalMax("30.0") BigDecimal maxWeightKg,
        @NotNull @DecimalMin("0.0") BigDecimal baseFee,
        @NotNull @DecimalMin("0.0") BigDecimal perKgFee,
        Boolean active
) {
}
