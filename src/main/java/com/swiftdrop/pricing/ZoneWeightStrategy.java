package com.swiftdrop.pricing;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Main strategy: find the admin's rule for the destination zone and weight band.
 * fee = baseFee + perKgFee x (weight - band minimum) + size surcharge
 */
@Component
@Order(1)
public class ZoneWeightStrategy implements PricingStrategy {

    @Override
    public String name() {
        return "ZONE_WEIGHT";
    }

    @Override
    public boolean supports(List<PricingRule> zoneRules, BigDecimal weightKg) {
        return zoneRules.stream().anyMatch(rule -> rule.covers(weightKg));
    }

    @Override
    public BigDecimal calculate(List<PricingRule> zoneRules, BigDecimal weightKg, ParcelSize size) {
        PricingRule rule = zoneRules.stream()
                .filter(r -> r.covers(weightKg))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No rule covers this weight"));

        BigDecimal extraKg = weightKg.subtract(rule.getMinWeightKg()).max(BigDecimal.ZERO);
        return rule.getBaseFee()
                .add(rule.getPerKgFee().multiply(extraKg))
                .add(size.surcharge())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
