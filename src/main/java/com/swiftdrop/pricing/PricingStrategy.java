package com.swiftdrop.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy pattern (report 7.2): each way of calculating a fee is a separate class.
 * A new pricing rule type = a new class; PricingService does not change (Open/Closed principle).
 */
public interface PricingStrategy {

    /** Short name shown in the fee breakdown, e.g. "ZONE_WEIGHT". */
    String name();

    /** True if this strategy can price the parcel with the given rules. */
    boolean supports(List<PricingRule> zoneRules, BigDecimal weightKg);

    BigDecimal calculate(List<PricingRule> zoneRules, BigDecimal weightKg, ParcelSize size);
}
