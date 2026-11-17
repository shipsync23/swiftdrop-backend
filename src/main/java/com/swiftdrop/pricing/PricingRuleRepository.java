package com.swiftdrop.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Integer> {

    List<PricingRule> findByZoneIdAndActiveTrueOrderByMinWeightKgAsc(Integer zoneId);
}
