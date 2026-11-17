package com.swiftdrop.parcel.dto;

import java.math.BigDecimal;

public record FeeEstimateResponse(
        BigDecimal fee,
        String currency,
        Integer destZoneId,
        String destZone,
        String pricingStrategy
) {
}
