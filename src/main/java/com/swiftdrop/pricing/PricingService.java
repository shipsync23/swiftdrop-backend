package com.swiftdrop.pricing;

import com.swiftdrop.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculates delivery fees. It only knows the PricingStrategy interface; the strategies
 * are injected by Spring in @Order and the first one that supports the parcel is used.
 * Single responsibility: this class only calculates fees.
 */
@Service
public class PricingService {

    public static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("30.00");

    private final List<PricingStrategy> strategies;
    private final ZoneRepository zones;
    private final PricingRuleRepository rules;

    public PricingService(List<PricingStrategy> strategies, ZoneRepository zones, PricingRuleRepository rules) {
        this.strategies = strategies;
        this.zones = zones;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public FeeQuote quote(Integer destZoneId, BigDecimal weightKg, ParcelSize size) {
        Zone zone = zones.findById(destZoneId)
                .filter(Zone::isActive)
                .orElseThrow(() -> ApiException.badRequest("We do not deliver to this zone"));
        if (weightKg.compareTo(MAX_WEIGHT_KG) > 0) {
            throw ApiException.badRequest("Maximum parcel weight is 30 kg");
        }

        List<PricingRule> zoneRules = rules.findByZoneIdAndActiveTrueOrderByMinWeightKgAsc(zone.getId());
        PricingStrategy strategy = strategies.stream()
                .filter(s -> s.supports(zoneRules, weightKg))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Pricing is not available for this parcel"));

        BigDecimal fee = strategy.calculate(zoneRules, weightKg, size);
        return new FeeQuote(zone, fee, strategy.name());
    }

    /** Result of a fee calculation. */
    public record FeeQuote(Zone zone, BigDecimal fee, String strategy) {
    }
}
