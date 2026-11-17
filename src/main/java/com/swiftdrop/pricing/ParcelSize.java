package com.swiftdrop.pricing;

import java.math.BigDecimal;

/** Parcel size chosen by the customer. Bigger parcels take more space in the vehicle, so they cost more. */
public enum ParcelSize {
    SMALL(new BigDecimal("0.00")),
    MEDIUM(new BigDecimal("100.00")),
    LARGE(new BigDecimal("250.00"));

    private final BigDecimal surcharge;

    ParcelSize(BigDecimal surcharge) {
        this.surcharge = surcharge;
    }

    public BigDecimal surcharge() {
        return surcharge;
    }
}
