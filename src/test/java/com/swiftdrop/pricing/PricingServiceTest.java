package com.swiftdrop.pricing;

import com.swiftdrop.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Strategy pattern for pricing (report 7.2). */
class PricingServiceTest {

    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final PricingRuleRepository rules = mock(PricingRuleRepository.class);
    private PricingService service;
    private Zone kandy;

    @BeforeEach
    void setUp() {
        service = new PricingService(
                List.of(new ZoneWeightStrategy(), new FlatRateStrategy(new BigDecimal("600"), new BigDecimal("100"))),
                zones, rules);
        kandy = zone(3, "Kandy", true);
        when(zones.findById(3)).thenReturn(Optional.of(kandy));
    }

    @Test
    void usesTheZoneWeightRuleThatCoversTheWeight() {
        when(rules.findByZoneIdAndActiveTrueOrderByMinWeightKgAsc(3)).thenReturn(List.of(
                rule(kandy, "0", "2", "500", "0"),
                rule(kandy, "2", "5", "550", "60")));

        PricingService.FeeQuote quote = service.quote(3, new BigDecimal("3.5"), ParcelSize.MEDIUM);

        // 550 + 60 x (3.5 - 2) + 100 (medium surcharge) = 740
        assertThat(quote.fee()).isEqualByComparingTo("740.00");
        assertThat(quote.strategy()).isEqualTo("ZONE_WEIGHT");
        assertThat(quote.zone().getName()).isEqualTo("Kandy");
    }

    @Test
    void fallsBackToFlatRateWhenNoRuleCoversTheWeight() {
        when(rules.findByZoneIdAndActiveTrueOrderByMinWeightKgAsc(3)).thenReturn(List.of());

        PricingService.FeeQuote quote = service.quote(3, new BigDecimal("2"), ParcelSize.SMALL);

        // 600 + 100 x 2 = 800
        assertThat(quote.fee()).isEqualByComparingTo("800.00");
        assertThat(quote.strategy()).isEqualTo("FLAT_RATE");
    }

    @Test
    void rejectsInactiveOrUnknownZones() {
        when(zones.findById(9)).thenReturn(Optional.of(zone(9, "Closed", false)));
        when(zones.findById(10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quote(9, BigDecimal.ONE, ParcelSize.SMALL)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.quote(10, BigDecimal.ONE, ParcelSize.SMALL)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsParcelsHeavierThan30Kg() {
        assertThatThrownBy(() -> service.quote(3, new BigDecimal("31"), ParcelSize.LARGE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("30 kg");
    }

    @Test
    void weightBandsDoNotOverlapAtTheBoundary() {
        PricingRule first = rule(kandy, "0", "2", "500", "0");
        PricingRule second = rule(kandy, "2", "5", "550", "60");
        assertThat(first.covers(new BigDecimal("2"))).isTrue();
        assertThat(second.covers(new BigDecimal("2"))).isFalse();
        assertThat(second.covers(new BigDecimal("2.01"))).isTrue();
    }

    static Zone zone(int id, String name, boolean active) {
        Zone z = new Zone();
        z.setId(id);
        z.setName(name);
        z.setActive(active);
        return z;
    }

    static PricingRule rule(Zone zone, String min, String max, String base, String perKg) {
        PricingRule r = new PricingRule();
        r.setZone(zone);
        r.setMinWeightKg(new BigDecimal(min));
        r.setMaxWeightKg(new BigDecimal(max));
        r.setBaseFee(new BigDecimal(base));
        r.setPerKgFee(new BigDecimal(perKg));
        r.setActive(true);
        return r;
    }
}
