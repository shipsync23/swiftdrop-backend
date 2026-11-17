package com.swiftdrop.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fallback strategy when the admin has not configured a rule for this zone/weight:
 * a flat rate per kg so a customer can still get a price.
 * Liskov substitution: it can replace ZoneWeightStrategy anywhere a fee is calculated.
 */
@Component
@Order(2)
public class FlatRateStrategy implements PricingStrategy {

    private final BigDecimal baseFee;
    private final BigDecimal perKgFee;

    public FlatRateStrategy(@Value("${swiftdrop.pricing.flat-base-fee:600}") BigDecimal baseFee,
                            @Value("${swiftdrop.pricing.flat-per-kg-fee:100}") BigDecimal perKgFee) {
        this.baseFee = baseFee;
        this.perKgFee = perKgFee;
    }

    @Override
    public String name() {
        return "FLAT_RATE";
    }

    @Override
    public boolean supports(List<PricingRule> zoneRules, BigDecimal weightKg) {
        return true;
    }

    @Override
    public BigDecimal calculate(List<PricingRule> zoneRules, BigDecimal weightKg, ParcelSize size) {
        return baseFee
                .add(perKgFee.multiply(weightKg))
                .add(size.surcharge())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
