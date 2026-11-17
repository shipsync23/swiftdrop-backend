package com.swiftdrop.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fee rule for one zone and one weight band:
 * fee = baseFee + perKgFee x (weight - minWeightKg)
 */
@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "min_weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal minWeightKg;

    @Column(name = "max_weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxWeightKg;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKgFee;

    @Column(nullable = false)
    private boolean active = true;

    /** Lower bound inclusive except for the first band, upper bound inclusive: (min, max]. */
    public boolean covers(BigDecimal weightKg) {
        boolean aboveMin = minWeightKg.signum() == 0
                ? weightKg.signum() > 0
                : weightKg.compareTo(minWeightKg) > 0;
        return aboveMin && weightKg.compareTo(maxWeightKg) <= 0;
    }
}
